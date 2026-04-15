package com.project.javasecurityoptimizer.analysis;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.project.javasecurityoptimizer.analysis.rules.ArrayEqualsRule;
import com.project.javasecurityoptimizer.analysis.rules.BlockingCallInParallelStreamRule;
import com.project.javasecurityoptimizer.analysis.rules.BooleanLiteralComparisonRule;
import com.project.javasecurityoptimizer.analysis.rules.CollectionContainsInLoopRule;
import com.project.javasecurityoptimizer.analysis.rules.DeepNestingRule;
import com.project.javasecurityoptimizer.analysis.rules.EmptyCatchBlockRule;
import com.project.javasecurityoptimizer.analysis.rules.EqualsHashCodeContractRule;
import com.project.javasecurityoptimizer.analysis.rules.GenericExceptionCatchRule;
import com.project.javasecurityoptimizer.analysis.rules.HardcodedCredentialRule;
import com.project.javasecurityoptimizer.analysis.rules.LongMethodRule;
import com.project.javasecurityoptimizer.analysis.rules.LoopStringConcatenationRule;
import com.project.javasecurityoptimizer.analysis.rules.MagicNumberRule;
import com.project.javasecurityoptimizer.analysis.rules.MultipleReturnRule;
import com.project.javasecurityoptimizer.analysis.rules.NullDereferenceRule;
import com.project.javasecurityoptimizer.analysis.rules.OptionalGetWithoutIsPresentRule;
import com.project.javasecurityoptimizer.analysis.rules.ResourceNotClosedRule;
import com.project.javasecurityoptimizer.analysis.rules.StringEqualityRule;
import com.project.javasecurityoptimizer.analysis.rules.ThreadSleepInLoopRule;
import com.project.javasecurityoptimizer.analysis.rules.TooManyParametersRule;
import com.project.javasecurityoptimizer.analysis.rules.UnusedPrivateFieldRule;
import com.project.javasecurityoptimizer.rulepack.InMemoryRulePackLocalRepository;
import com.project.javasecurityoptimizer.rulepack.RulePackLocalRepository;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@Service
public class JavaAnalysisEngine {
    private static final int AST_CACHE_MAX_ENTRIES = 2_000;
    private static final int SYMBOL_CACHE_MAX_ENTRIES = 2_000;
    private static final int RULE_CACHE_MAX_ENTRIES = 20_000;
    private static final long MMAP_PARSE_THRESHOLD_BYTES = 512 * 1024L;
    private static final Set<String> DEFAULT_DEGRADE_RULE_SET = Set.of(
            "JAVA.STRING.EQUALITY",
            "JAVA.NPE.DEREFERENCE",
            "JAVA.SECURITY.HARDCODED_CREDENTIAL",
            "JAVA.EXCEPTION.GENERIC_CATCH",
            "JAVA.RESOURCE.NOT_CLOSED"
    );

    private final Map<String, JavaRule> ruleRegistry;
    private final RulePackLocalRepository localRepository;
    private final Map<String, CachedAst> astCache = new ConcurrentHashMap<>();
    private final Map<String, CachedSymbolIndex> symbolIndexCache = new ConcurrentHashMap<>();
    private final Map<String, CachedRuleResult> ruleResultCache = new ConcurrentHashMap<>();

    public JavaAnalysisEngine() {
        this(List.of(
                new NullDereferenceRule(),
                new ResourceNotClosedRule(),
                new StringEqualityRule(),
                new LoopStringConcatenationRule(),
                new EmptyCatchBlockRule(),
                new GenericExceptionCatchRule(),
                new ThreadSleepInLoopRule(),
                new HardcodedCredentialRule(),
                new OptionalGetWithoutIsPresentRule(),
                new ArrayEqualsRule(),
                new EqualsHashCodeContractRule(),
                new LongMethodRule(),
                new TooManyParametersRule(),
                new BooleanLiteralComparisonRule(),
                new MultipleReturnRule(),
                new DeepNestingRule(),
                new CollectionContainsInLoopRule(),
                new BlockingCallInParallelStreamRule(),
                new UnusedPrivateFieldRule(),
                new MagicNumberRule()
        ), new InMemoryRulePackLocalRepository());
    }

    public JavaAnalysisEngine(List<JavaRule> rules) {
        this(rules, new InMemoryRulePackLocalRepository());
    }

    public JavaAnalysisEngine(List<JavaRule> rules, RulePackLocalRepository localRepository) {
        this.ruleRegistry = new LinkedHashMap<>();
        this.localRepository = localRepository;
        for (JavaRule rule : rules) {
            ruleRegistry.put(rule.id(), rule);
        }
        if (this.localRepository.activeRuleSet().isEmpty()) {
            this.localRepository.setActiveRuleSet(ruleRegistry.keySet());
        }
    }

    public AnalyzeTaskResult analyze(AnalyzeTaskRequest request) {
        return analyzeWithMetrics(request);
    }

    public AnalyzeTaskResult analyzeWithMetrics(AnalyzeTaskRequest request) {
        Instant start = Instant.now();
        ExecutionContext executionContext = new ExecutionContext();
        List<ProgressEvent> events = new ArrayList<>();
        events.add(ProgressEvent.of("index", 5, "开始索引 Java 源文件"));

        IndexResult indexResult = indexFiles(request, executionContext);
        events.add(ProgressEvent.of("index", 25, "索引完成，候选文件数: " + indexResult.indexedFiles().size()));
        events.addAll(indexResult.events());

        events.add(ProgressEvent.of("parse", 30, "开始解析 AST"));
        ParseResult parseResult = parseFiles(indexResult.indexedFiles(), request, executionContext);
        events.add(ProgressEvent.of("parse", 55, "解析完成，成功: " + parseResult.parsedSources().size()
                + "，跳过: " + (indexResult.skippedFiles() + parseResult.failedFiles())));
        events.addAll(parseResult.events());

        List<JavaRule> selectedRules = resolveRules(request.ruleSet());
        List<JavaRule> degradeRules = resolveDegradeRules(request, selectedRules);
        events.add(ProgressEvent.of("analyze", 60, "开始执行规则"));
        RuleRunResult ruleRunResult = runRules(
                parseResult.parsedSources(),
                parseResult.fileFingerprint(),
                parseResult.degradedFiles(),
                selectedRules,
                degradeRules,
                request.ruleConcurrency(),
                request.ruleTimeout(),
                executionContext
        );
        List<AnalysisIssue> issues = ruleRunResult.issues();
        events.addAll(ruleRunResult.events());

        events.add(ProgressEvent.of("analyze", 80, "规则执行完成，发现问题: " + issues.size()));
        events.add(ProgressEvent.of("report", 90, "生成分析报告"));
        AnalysisStats stats = new AnalysisStats(
                indexResult.indexedFiles().size(),
                parseResult.parsedSources().size(),
                indexResult.skippedFiles() + parseResult.failedFiles(),
                issues.size(),
                ruleRunResult.ruleHitCounts().values().stream().mapToInt(Integer::intValue).sum(),
                ruleRunResult.failedRuleIds().size()
        );
        long durationMillis = Duration.between(start, Instant.now()).toMillis();
        events.add(ProgressEvent.of("report", 100, "分析完成，耗时(ms): " + durationMillis));

        RuleExecutionMetrics metrics = new RuleExecutionMetrics(
                ruleRunResult.ruleHitCounts(),
                ruleRunResult.ruleDurationMillis()
        );
        AnalysisExecutionReport executionReport = new AnalysisExecutionReport(
                new CacheStats(
                        executionContext.astCacheHits,
                        executionContext.astCacheMisses,
                        executionContext.symbolCacheHits,
                        executionContext.symbolCacheMisses,
                        executionContext.ruleCacheHits,
                        executionContext.ruleCacheMisses
                ),
                executionContext.degradedFiles,
                executionContext.failedItems
        );
        return new AnalyzeTaskResult(issues, stats, durationMillis, events, metrics, executionReport);
    }

    private List<JavaRule> resolveRules(Set<String> requestedRuleIds) {
        Set<String> resolvedRuleIds = requestedRuleIds;
        if (resolvedRuleIds == null || resolvedRuleIds.isEmpty()) {
            resolvedRuleIds = localRepository.activeRuleSet();
        }
        if (resolvedRuleIds == null || resolvedRuleIds.isEmpty()) {
            return new ArrayList<>(ruleRegistry.values());
        }
        List<JavaRule> selected = new ArrayList<>();
        for (String ruleId : resolvedRuleIds) {
            JavaRule rule = ruleRegistry.get(ruleId);
            if (rule != null) {
                selected.add(rule);
            }
        }
        return selected.isEmpty() ? new ArrayList<>(ruleRegistry.values()) : selected;
    }

    private List<JavaRule> resolveDegradeRules(AnalyzeTaskRequest request, List<JavaRule> selectedRules) {
        Set<String> degradeIds = request.degradeRuleSet() == null || request.degradeRuleSet().isEmpty()
                ? DEFAULT_DEGRADE_RULE_SET
                : request.degradeRuleSet();
        List<JavaRule> degradeRules = new ArrayList<>();
        Set<String> selectedIds = new HashSet<>();
        for (JavaRule selectedRule : selectedRules) {
            selectedIds.add(selectedRule.id());
        }
        for (String degradeId : degradeIds) {
            if (!selectedIds.contains(degradeId)) {
                continue;
            }
            JavaRule rule = ruleRegistry.get(degradeId);
            if (rule != null) {
                degradeRules.add(rule);
            }
        }
        return degradeRules.isEmpty() ? selectedRules : degradeRules;
    }

    private IndexResult indexFiles(AnalyzeTaskRequest request, ExecutionContext executionContext) {
        List<ProgressEvent> events = new ArrayList<>();
        List<FileIndexInfo> indexedFiles = new ArrayList<>();
        int skipped = 0;

        if (request.mode() == AnalyzeMode.INCREMENTAL && request.changedFiles() != null && !request.changedFiles().isEmpty()) {
            List<Path> incrementalCandidates = new ArrayList<>();
            incrementalCandidates.addAll(request.changedFiles());
            incrementalCandidates.addAll(request.impactedFiles());
            for (Path changed : incrementalCandidates) {
                Path absolute = normalizePath(request.projectPath(), changed);
                if (isJavaFile(absolute) && Files.exists(absolute)) {
                    long size = fileSize(absolute);
                    if (size > request.maxFileSizeBytes()) {
                        skipped++;
                        events.add(ProgressEvent.of("index", 20, absolute.toString(), "跳过超大文件"));
                    } else {
                        boolean degraded = size >= request.degradeFileSizeBytes();
                        if (degraded) {
                            executionContext.degradedFiles.add(absolute.toString());
                            events.add(ProgressEvent.of("index", 22, absolute.toString(), "文件触发降级规则集"));
                        }
                        indexedFiles.add(new FileIndexInfo(absolute, size, degraded));
                    }
                }
            }
            indexedFiles.sort(Comparator.comparing(FileIndexInfo::path));
            return new IndexResult(indexedFiles, skipped, events);
        }

        try (var stream = Files.walk(request.projectPath())) {
            for (Path file : stream.filter(Files::isRegularFile).toList()) {
                if (!isJavaFile(file)) {
                    continue;
                }
                long size = fileSize(file);
                if (size > request.maxFileSizeBytes()) {
                    skipped++;
                    events.add(ProgressEvent.of("index", 20, file.toString(), "跳过超大文件"));
                    continue;
                }
                boolean degraded = size >= request.degradeFileSizeBytes();
                if (degraded) {
                    executionContext.degradedFiles.add(file.toString());
                    events.add(ProgressEvent.of("index", 22, file.toString(), "文件触发降级规则集"));
                }
                indexedFiles.add(new FileIndexInfo(file, size, degraded));
            }
        } catch (IOException e) {
            events.add(ProgressEvent.of("index", 20, "索引失败: " + e.getMessage()));
        }

        indexedFiles.sort(Comparator.comparing(FileIndexInfo::path));
        return new IndexResult(indexedFiles, skipped, events);
    }

    private ParseResult parseFiles(List<FileIndexInfo> files, AnalyzeTaskRequest request, ExecutionContext executionContext) {
        List<ProgressEvent> events = new ArrayList<>();
        if (files.isEmpty()) {
            return new ParseResult(List.of(), Map.of(), Set.of(), 0, events);
        }

        ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();
        Semaphore parseSemaphore = new Semaphore(Math.max(1, request.parseConcurrency()));
        try {
            List<Future<ParsedSource>> futures = new ArrayList<>();
            for (FileIndexInfo file : files) {
                Path path = file.path();
                String fingerprint = fingerprint(path, file.sizeBytes());
                CachedAst cachedAst = astCache.get(pathKey(path));
                if (cachedAst != null && cachedAst.fingerprint().equals(fingerprint)) {
                    executionContext.astCacheHits++;
                    ensureSymbolIndex(path, fingerprint, cachedAst.compilationUnit(), executionContext);
                    futures.add(java.util.concurrent.CompletableFuture.completedFuture(new ParsedSource(path, cachedAst.compilationUnit().clone())));
                    continue;
                }
                executionContext.astCacheMisses++;
                Callable<ParsedSource> task = () -> {
                    parseSemaphore.acquire();
                    try {
                        return new ParsedSource(path, parseCompilationUnit(path, file.sizeBytes()));
                    } finally {
                        parseSemaphore.release();
                    }
                };
                futures.add(pool.submit(task));
            }

            List<ParsedSource> parsed = new ArrayList<>();
            Map<Path, String> fileFingerprint = new LinkedHashMap<>();
            Set<Path> degradedFiles = new HashSet<>();
            int failed = 0;
            for (int i = 0; i < futures.size(); i++) {
                Future<ParsedSource> future = futures.get(i);
                FileIndexInfo fileInfo = files.get(i);
                Path file = fileInfo.path();
                String fingerprint = fingerprint(file, fileInfo.sizeBytes());
                try {
                    ParsedSource parsedSource = future.get(request.parseTimeout().toMillis(), TimeUnit.MILLISECONDS);
                    parsed.add(parsedSource);
                    fileFingerprint.put(file, fingerprint);
                    if (fileInfo.degraded()) {
                        degradedFiles.add(file);
                    }
                    putWithBound(astCache, pathKey(file), new CachedAst(fingerprint, parsedSource.compilationUnit().clone()), AST_CACHE_MAX_ENTRIES);
                    ensureSymbolIndex(file, fingerprint, parsedSource.compilationUnit(), executionContext);
                } catch (Exception e) {
                    future.cancel(true);
                    ParsedSource retryParsed = retryParse(pool, parseSemaphore, file, fileInfo, request, events);
                    if (retryParsed != null) {
                        parsed.add(retryParsed);
                        fileFingerprint.put(file, fingerprint);
                        if (fileInfo.degraded()) {
                            degradedFiles.add(file);
                        }
                        putWithBound(astCache, pathKey(file), new CachedAst(fingerprint, retryParsed.compilationUnit().clone()), AST_CACHE_MAX_ENTRIES);
                        ensureSymbolIndex(file, fingerprint, retryParsed.compilationUnit(), executionContext);
                    } else {
                        failed++;
                        String reason = rootMessage(e);
                        events.add(ProgressEvent.of("parse", 45, file.toString(), "解析失败，原因: " + reason));
                        executionContext.failedItems.add("parse file=" + file + " reason=" + reason);
                    }
                }
            }
            return new ParseResult(parsed, fileFingerprint, degradedFiles, failed, events);
        } finally {
            pool.shutdown();
        }
    }

    private ParsedSource retryParse(
            ExecutorService pool,
            Semaphore parseSemaphore,
            Path file,
            FileIndexInfo fileInfo,
            AnalyzeTaskRequest request,
            List<ProgressEvent> events
    ) {
        for (int attempt = 1; attempt <= request.parseRetryCount(); attempt++) {
            try {
                Future<ParsedSource> retryFuture = pool.submit(() -> {
                    parseSemaphore.acquire();
                    try {
                        return new ParsedSource(file, parseCompilationUnit(file, fileInfo.sizeBytes()));
                    } finally {
                        parseSemaphore.release();
                    }
                });
                ParsedSource parsed = retryFuture.get(request.parseTimeout().toMillis(), TimeUnit.MILLISECONDS);
                events.add(ProgressEvent.of("parse", 40, file.toString(), "解析重试成功，第 " + attempt + " 次"));
                return parsed;
            } catch (Exception retryException) {
                events.add(ProgressEvent.of("parse", 42, file.toString(),
                        "解析重试失败，第 " + attempt + " 次，原因: " + rootMessage(retryException)));
            }
        }
        return null;
    }

    private com.github.javaparser.ast.CompilationUnit parseCompilationUnit(Path file, long sizeBytes) throws IOException {
        if (sizeBytes >= MMAP_PARSE_THRESHOLD_BYTES) {
            try (FileChannel fileChannel = FileChannel.open(file, StandardOpenOption.READ)) {
                MappedByteBuffer mappedByteBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, fileChannel.size());
                String source = StandardCharsets.UTF_8.decode(mappedByteBuffer).toString();
                try {
                    return StaticJavaParser.parse(source);
                } catch (RuntimeException parseException) {
                    // Fallback for edge cases (e.g., non-UTF8 source)
                    return StaticJavaParser.parse(file);
                }
            }
        }
        return StaticJavaParser.parse(file);
    }

    private void ensureSymbolIndex(
            Path file,
            String fingerprint,
            com.github.javaparser.ast.CompilationUnit compilationUnit,
            ExecutionContext executionContext
    ) {
        CachedSymbolIndex cachedIndex = symbolIndexCache.get(pathKey(file));
        if (cachedIndex != null && cachedIndex.fingerprint().equals(fingerprint)) {
            executionContext.symbolCacheHits++;
            return;
        }
        executionContext.symbolCacheMisses++;
        List<String> typeNames = compilationUnit.findAll(ClassOrInterfaceDeclaration.class).stream()
                .map(type -> type.getFullyQualifiedName().orElse(type.getNameAsString()))
                .toList();
        putWithBound(symbolIndexCache, pathKey(file), new CachedSymbolIndex(fingerprint, typeNames), SYMBOL_CACHE_MAX_ENTRIES);
    }

    private RuleRunResult runRules(
            List<ParsedSource> parsedSources,
            Map<Path, String> fileFingerprint,
            Set<Path> degradedFiles,
            List<JavaRule> rules,
            List<JavaRule> degradeRules,
            int concurrency,
            Duration timeoutPerRule,
            ExecutionContext executionContext
    ) {
        List<AnalysisIssue> issues = java.util.Collections.synchronizedList(new ArrayList<>());
        Map<String, Integer> ruleHitCounts = new LinkedHashMap<>();
        Map<String, Long> ruleDurationMillis = new LinkedHashMap<>();
        Set<String> failedRuleIds = new java.util.HashSet<>();
        List<ProgressEvent> events = new ArrayList<>();
        if (parsedSources.isEmpty() || rules.isEmpty()) {
            return new RuleRunResult(List.of(), ruleHitCounts, ruleDurationMillis, failedRuleIds, events);
        }

        ExecutorService rulePool = Executors.newVirtualThreadPerTaskExecutor();
        Semaphore ruleSemaphore = new Semaphore(Math.max(1, concurrency));
        try {
            List<SubmittedRuleTask> submittedTasks = new ArrayList<>();
            for (ParsedSource parsedSource : parsedSources) {
                RuleContext context = new RuleContext(parsedSource.filePath());
                List<JavaRule> fileRules = degradedFiles.contains(parsedSource.filePath()) ? degradeRules : rules;
                for (JavaRule rule : fileRules) {
                    String cacheKey = ruleCacheKey(fileFingerprint.get(parsedSource.filePath()), rule.id());
                    CachedRuleResult cachedRuleResult = cacheKey == null ? null : ruleResultCache.get(cacheKey);
                    if (cachedRuleResult != null) {
                        executionContext.ruleCacheHits++;
                        issues.addAll(cachedRuleResult.issues());
                        ruleHitCounts.merge(rule.id(), cachedRuleResult.issues().size(), Integer::sum);
                        ruleDurationMillis.merge(rule.id(), cachedRuleResult.durationMillis(), Long::sum);
                        continue;
                    }
                    executionContext.ruleCacheMisses++;
                    Future<RuleExecutionOutcome> future = rulePool.submit(new RuleTask(rule, parsedSource, context, ruleSemaphore));
                    submittedTasks.add(new SubmittedRuleTask(rule.id(), parsedSource.filePath(), cacheKey, future));
                }
            }

            for (SubmittedRuleTask submittedTask : submittedTasks) {
                try {
                    RuleExecutionOutcome outcome = submittedTask.future().get(timeoutPerRule.toMillis(), TimeUnit.MILLISECONDS);
                    issues.addAll(outcome.issues());
                    ruleHitCounts.merge(outcome.ruleId(), outcome.issues().size(), Integer::sum);
                    ruleDurationMillis.merge(outcome.ruleId(), outcome.durationMillis(), Long::sum);
                    if (submittedTask.cacheKey() != null) {
                        putWithBound(
                                ruleResultCache,
                                submittedTask.cacheKey(),
                                new CachedRuleResult(outcome.durationMillis(), List.copyOf(outcome.issues())),
                                RULE_CACHE_MAX_ENTRIES
                        );
                    }
                } catch (Exception e) {
                    submittedTask.future().cancel(true);
                    String message = rootMessage(e);
                    failedRuleIds.add(submittedTask.ruleId());
                    events.add(ProgressEvent.of(
                            "analyze",
                            70,
                            submittedTask.filePath().toString(),
                            "规则执行失败 rule=" + submittedTask.ruleId() + "，原因: " + message
                    ));
                    executionContext.failedItems.add(
                            "rule=" + submittedTask.ruleId() + " file=" + submittedTask.filePath() + " reason=" + message
                    );
                }
            }
        } finally {
            rulePool.shutdown();
        }
        return new RuleRunResult(issues, ruleHitCounts, ruleDurationMillis, failedRuleIds, events);
    }

    private long fileSize(Path path) {
        try {
            return Files.size(path);
        } catch (IOException e) {
            return Long.MAX_VALUE;
        }
    }

    private boolean isJavaFile(Path path) {
        return path.toString().endsWith(".java");
    }

    private Path normalizePath(Path projectPath, Path candidate) {
        return candidate.isAbsolute() ? candidate.normalize() : projectPath.resolve(candidate).normalize();
    }

    private String rootMessage(Exception e) {
        Throwable current = e;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private <K, V> void putWithBound(Map<K, V> cache, K key, V value, int maxEntries) {
        if (cache.size() >= maxEntries) {
            cache.clear();
        }
        cache.put(key, value);
    }

    private String fingerprint(Path file, long fallbackSize) {
        try {
            FileTime lastModifiedTime = Files.getLastModifiedTime(file);
            long size = Files.size(file);
            return size + ":" + lastModifiedTime.toMillis();
        } catch (IOException e) {
            return fallbackSize + ":" + System.currentTimeMillis();
        }
    }

    private String pathKey(Path path) {
        return path.toAbsolutePath().normalize().toString();
    }

    private String ruleCacheKey(String fingerprint, String ruleId) {
        if (fingerprint == null || ruleId == null) {
            return null;
        }
        return fingerprint + "#" + ruleId;
    }

    private record IndexResult(List<FileIndexInfo> indexedFiles, int skippedFiles, List<ProgressEvent> events) {
    }

    private record ParseResult(
            List<ParsedSource> parsedSources,
            Map<Path, String> fileFingerprint,
            Set<Path> degradedFiles,
            int failedFiles,
            List<ProgressEvent> events
    ) {
    }

    private record FileIndexInfo(
            Path path,
            long sizeBytes,
            boolean degraded
    ) {
    }

    private record RuleRunResult(
            List<AnalysisIssue> issues,
            Map<String, Integer> ruleHitCounts,
            Map<String, Long> ruleDurationMillis,
            Set<String> failedRuleIds,
            List<ProgressEvent> events
    ) {
    }

    private record RuleExecutionOutcome(
            String ruleId,
            List<AnalysisIssue> issues,
            long durationMillis
    ) {
    }

    private record SubmittedRuleTask(
            String ruleId,
            Path filePath,
            String cacheKey,
            Future<RuleExecutionOutcome> future
    ) {
    }

    private record CachedAst(
            String fingerprint,
            com.github.javaparser.ast.CompilationUnit compilationUnit
    ) {
    }

    private record CachedSymbolIndex(
            String fingerprint,
            List<String> symbols
    ) {
    }

    private record CachedRuleResult(
            long durationMillis,
            List<AnalysisIssue> issues
    ) {
    }

    private static final class ExecutionContext {
        private int astCacheHits;
        private int astCacheMisses;
        private int symbolCacheHits;
        private int symbolCacheMisses;
        private int ruleCacheHits;
        private int ruleCacheMisses;
        private final List<String> degradedFiles = new ArrayList<>();
        private final List<String> failedItems = new ArrayList<>();
    }

    private static final class RuleTask implements Callable<RuleExecutionOutcome> {
        private final JavaRule rule;
        private final ParsedSource parsedSource;
        private final RuleContext context;
        private final Semaphore ruleSemaphore;

        private RuleTask(JavaRule rule, ParsedSource parsedSource, RuleContext context, Semaphore ruleSemaphore) {
            this.rule = rule;
            this.parsedSource = parsedSource;
            this.context = context;
            this.ruleSemaphore = ruleSemaphore;
        }

        @Override
        public RuleExecutionOutcome call() {
            long start = System.nanoTime();
            try {
                ruleSemaphore.acquire();
                List<AnalysisIssue> found;
                try {
                    found = rule.analyze(parsedSource.compilationUnit(), context);
                } finally {
                    ruleSemaphore.release();
                }
                long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
                return new RuleExecutionOutcome(rule.id(), found, elapsedMillis);
            } catch (Exception e) {
                throw new IllegalStateException(
                        "rule=" + rule.id() + " file=" + parsedSource.filePath() + " message=" + rootMessageStatic(e), e
                );
            }
        }

        private static String rootMessageStatic(Exception e) {
            Throwable current = e;
            while (current.getCause() != null) {
                current = current.getCause();
            }
            return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
        }
    }

    public Collection<String> availableRuleIds() {
        return ruleRegistry.keySet();
    }
}

package com.project.javasecurityoptimizer.analysis;

import com.github.javaparser.StaticJavaParser;
import com.project.javasecurityoptimizer.analysis.rules.EmptyCatchBlockRule;
import com.project.javasecurityoptimizer.analysis.rules.GenericExceptionCatchRule;
import com.project.javasecurityoptimizer.analysis.rules.HardcodedCredentialRule;
import com.project.javasecurityoptimizer.analysis.rules.LoopStringConcatenationRule;
import com.project.javasecurityoptimizer.analysis.rules.NullDereferenceRule;
import com.project.javasecurityoptimizer.analysis.rules.ResourceNotClosedRule;
import com.project.javasecurityoptimizer.analysis.rules.StringEqualityRule;
import com.project.javasecurityoptimizer.analysis.rules.ThreadSleepInLoopRule;
import com.project.javasecurityoptimizer.rulepack.InMemoryRulePackLocalRepository;
import com.project.javasecurityoptimizer.rulepack.RulePackLocalRepository;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Service
public class JavaAnalysisEngine {
    private final Map<String, JavaRule> ruleRegistry;
    private final RulePackLocalRepository localRepository;

    public JavaAnalysisEngine() {
        this(List.of(
                new NullDereferenceRule(),
                new ResourceNotClosedRule(),
                new StringEqualityRule(),
                new LoopStringConcatenationRule(),
                new EmptyCatchBlockRule(),
                new GenericExceptionCatchRule(),
                new ThreadSleepInLoopRule(),
                new HardcodedCredentialRule()
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
        Instant start = Instant.now();
        List<ProgressEvent> events = new ArrayList<>();
        events.add(ProgressEvent.of("index", 5, "开始索引 Java 源文件"));

        IndexResult indexResult = indexFiles(request);
        events.add(ProgressEvent.of("index", 25, "索引完成，候选文件数: " + indexResult.indexedFiles().size()));
        events.addAll(indexResult.events());

        events.add(ProgressEvent.of("parse", 30, "开始解析 AST"));
        ParseResult parseResult = parseFiles(indexResult.indexedFiles(), request.parseConcurrency(), request.parseTimeout());
        events.add(ProgressEvent.of("parse", 55, "解析完成，成功: " + parseResult.parsedSources().size()
                + "，跳过: " + (indexResult.skippedFiles() + parseResult.failedFiles())));
        events.addAll(parseResult.events());

        events.add(ProgressEvent.of("analyze", 60, "开始执行规则"));
        List<AnalysisIssue> issues = runRules(parseResult.parsedSources(), resolveRules(request.ruleSet()));
        events.add(ProgressEvent.of("analyze", 85, "规则执行完成，发现问题: " + issues.size()));

        events.add(ProgressEvent.of("report", 90, "生成分析报告"));
        AnalysisStats stats = new AnalysisStats(
                indexResult.indexedFiles().size(),
                parseResult.parsedSources().size(),
                indexResult.skippedFiles() + parseResult.failedFiles(),
                issues.size()
        );
        long durationMillis = Duration.between(start, Instant.now()).toMillis();
        events.add(ProgressEvent.of("report", 100, "分析完成，耗时(ms): " + durationMillis));

        return new AnalyzeTaskResult(issues, stats, durationMillis, events);
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

    private IndexResult indexFiles(AnalyzeTaskRequest request) {
        List<ProgressEvent> events = new ArrayList<>();
        List<Path> indexedFiles = new ArrayList<>();
        int skipped = 0;

        if (request.mode() == AnalyzeMode.INCREMENTAL && request.changedFiles() != null && !request.changedFiles().isEmpty()) {
            for (Path changed : request.changedFiles()) {
                Path absolute = changed.isAbsolute() ? changed : request.projectPath().resolve(changed);
                if (isJavaFile(absolute) && Files.exists(absolute)) {
                    long size = fileSize(absolute);
                    if (size > request.maxFileSizeBytes()) {
                        skipped++;
                        events.add(ProgressEvent.of("index", 20, absolute.toString(), "跳过超大文件"));
                    } else {
                        indexedFiles.add(absolute);
                    }
                }
            }
            indexedFiles.sort(Comparator.naturalOrder());
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
                indexedFiles.add(file);
            }
        } catch (IOException e) {
            events.add(ProgressEvent.of("index", 20, "索引失败: " + e.getMessage()));
        }

        indexedFiles.sort(Comparator.naturalOrder());
        return new IndexResult(indexedFiles, skipped, events);
    }

    private ParseResult parseFiles(List<Path> files, int concurrency, Duration timeoutPerFile) {
        List<ProgressEvent> events = new ArrayList<>();
        if (files.isEmpty()) {
            return new ParseResult(List.of(), 0, events);
        }

        ExecutorService pool = Executors.newFixedThreadPool(Math.max(1, concurrency));
        try {
            List<Future<ParsedSource>> futures = new ArrayList<>();
            for (Path file : files) {
                Callable<ParsedSource> task = () -> new ParsedSource(file, StaticJavaParser.parse(file));
                futures.add(pool.submit(task));
            }

            List<ParsedSource> parsed = new ArrayList<>();
            int failed = 0;
            for (int i = 0; i < futures.size(); i++) {
                Future<ParsedSource> future = futures.get(i);
                Path file = files.get(i);
                try {
                    ParsedSource parsedSource = future.get(timeoutPerFile.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
                    parsed.add(parsedSource);
                } catch (Exception e) {
                    failed++;
                    future.cancel(true);
                    events.add(ProgressEvent.of("parse", 45, file.toString(), "解析失败，原因: " + rootMessage(e)));
                }
            }
            return new ParseResult(parsed, failed, events);
        } finally {
            pool.shutdownNow();
        }
    }

    private List<AnalysisIssue> runRules(List<ParsedSource> parsedSources, List<JavaRule> rules) {
        List<AnalysisIssue> issues = new ArrayList<>();
        for (ParsedSource parsedSource : parsedSources) {
            RuleContext context = new RuleContext(parsedSource.filePath());
            for (JavaRule rule : rules) {
                issues.addAll(rule.analyze(parsedSource.compilationUnit(), context));
            }
        }
        return issues;
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

    private String rootMessage(Exception e) {
        Throwable current = e;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private record IndexResult(List<Path> indexedFiles, int skippedFiles, List<ProgressEvent> events) {
    }

    private record ParseResult(List<ParsedSource> parsedSources, int failedFiles, List<ProgressEvent> events) {
    }

    public Collection<String> availableRuleIds() {
        return ruleRegistry.keySet();
    }
}

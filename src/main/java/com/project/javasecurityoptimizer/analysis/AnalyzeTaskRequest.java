package com.project.javasecurityoptimizer.analysis;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public final class AnalyzeTaskRequest {
    private static final long DEFAULT_MAX_FILE_SIZE_BYTES = 2 * 1024 * 1024;
    private static final long DEFAULT_DEGRADE_FILE_SIZE_BYTES = 1024 * 1024;
    private static final int DEFAULT_PARSE_CONCURRENCY = Math.max(1, Runtime.getRuntime().availableProcessors() / 2);
    private static final Duration DEFAULT_PARSE_TIMEOUT = Duration.ofSeconds(3);
    private static final int DEFAULT_PARSE_RETRY_COUNT = 1;
    private static final int DEFAULT_RULE_CONCURRENCY = Math.max(1, Runtime.getRuntime().availableProcessors() / 2);
    private static final Duration DEFAULT_RULE_TIMEOUT = Duration.ofMillis(1500);

    private final Path projectPath;
    private final Set<String> ruleSet;
    private final AnalyzeMode mode;
    private final Set<Path> changedFiles;
    private final Set<Path> impactedFiles;
    private final long maxFileSizeBytes;
    private final long degradeFileSizeBytes;
    private final int parseConcurrency;
    private final Duration parseTimeout;
    private final int parseRetryCount;
    private final int ruleConcurrency;
    private final Duration ruleTimeout;
    private final Set<String> degradeRuleSet;

    public AnalyzeTaskRequest(
            Path projectPath,
            Set<String> ruleSet,
            AnalyzeMode mode,
            Set<Path> changedFiles,
            long maxFileSizeBytes,
            int parseConcurrency,
            Duration parseTimeout
    ) {
        this(projectPath, ruleSet, mode, changedFiles, Set.of(), maxFileSizeBytes, 0, parseConcurrency, parseTimeout, -1, 0, null, Set.of());
    }

    public AnalyzeTaskRequest(
            Path projectPath,
            Set<String> ruleSet,
            AnalyzeMode mode,
            Set<Path> changedFiles,
            Set<Path> impactedFiles,
            long maxFileSizeBytes,
            long degradeFileSizeBytes,
            int parseConcurrency,
            Duration parseTimeout,
            int parseRetryCount,
            int ruleConcurrency,
            Duration ruleTimeout,
            Set<String> degradeRuleSet
    ) {
        this.projectPath = Objects.requireNonNull(projectPath, "projectPath must not be null");
        this.ruleSet = ruleSet == null ? Set.of() : Collections.unmodifiableSet(new HashSet<>(ruleSet));
        this.mode = mode == null ? AnalyzeMode.FULL : mode;
        this.changedFiles = changedFiles == null ? Set.of() : Collections.unmodifiableSet(new HashSet<>(changedFiles));
        this.impactedFiles = impactedFiles == null ? Set.of() : Collections.unmodifiableSet(new HashSet<>(impactedFiles));
        this.maxFileSizeBytes = maxFileSizeBytes > 0 ? maxFileSizeBytes : DEFAULT_MAX_FILE_SIZE_BYTES;
        long normalizedDegradeSize = degradeFileSizeBytes > 0 ? degradeFileSizeBytes : DEFAULT_DEGRADE_FILE_SIZE_BYTES;
        this.degradeFileSizeBytes = Math.min(this.maxFileSizeBytes, normalizedDegradeSize);
        this.parseConcurrency = parseConcurrency > 0 ? parseConcurrency : DEFAULT_PARSE_CONCURRENCY;
        this.parseTimeout = parseTimeout == null || parseTimeout.isZero() || parseTimeout.isNegative()
                ? DEFAULT_PARSE_TIMEOUT : parseTimeout;
        this.parseRetryCount = parseRetryCount >= 0 ? parseRetryCount : DEFAULT_PARSE_RETRY_COUNT;
        this.ruleConcurrency = ruleConcurrency > 0 ? ruleConcurrency : DEFAULT_RULE_CONCURRENCY;
        this.ruleTimeout = ruleTimeout == null || ruleTimeout.isZero() || ruleTimeout.isNegative()
                ? DEFAULT_RULE_TIMEOUT : ruleTimeout;
        this.degradeRuleSet = degradeRuleSet == null
                ? Set.of()
                : Collections.unmodifiableSet(new HashSet<>(degradeRuleSet));
    }

    public static AnalyzeTaskRequest full(Path projectPath) {
        return new AnalyzeTaskRequest(projectPath, Set.of(), AnalyzeMode.FULL, Set.of(), 0, 0, null);
    }

    public Path projectPath() {
        return projectPath;
    }

    public Set<String> ruleSet() {
        return ruleSet;
    }

    public AnalyzeMode mode() {
        return mode;
    }

    public Set<Path> changedFiles() {
        return changedFiles;
    }

    public Set<Path> impactedFiles() {
        return impactedFiles;
    }

    public long maxFileSizeBytes() {
        return maxFileSizeBytes;
    }

    public long degradeFileSizeBytes() {
        return degradeFileSizeBytes;
    }

    public int parseConcurrency() {
        return parseConcurrency;
    }

    public Duration parseTimeout() {
        return parseTimeout;
    }

    public int parseRetryCount() {
        return parseRetryCount;
    }

    public int ruleConcurrency() {
        return ruleConcurrency;
    }

    public Duration ruleTimeout() {
        return ruleTimeout;
    }

    public Set<String> degradeRuleSet() {
        return degradeRuleSet;
    }
}

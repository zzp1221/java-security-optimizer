package com.project.javasecurityoptimizer.analysis;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public final class AnalyzeTaskRequest {
    private static final long DEFAULT_MAX_FILE_SIZE_BYTES = 2 * 1024 * 1024;
    private static final int DEFAULT_PARSE_CONCURRENCY = Math.max(1, Runtime.getRuntime().availableProcessors() / 2);
    private static final Duration DEFAULT_PARSE_TIMEOUT = Duration.ofSeconds(3);

    private final Path projectPath;
    private final Set<String> ruleSet;
    private final AnalyzeMode mode;
    private final Set<Path> changedFiles;
    private final long maxFileSizeBytes;
    private final int parseConcurrency;
    private final Duration parseTimeout;

    public AnalyzeTaskRequest(
            Path projectPath,
            Set<String> ruleSet,
            AnalyzeMode mode,
            Set<Path> changedFiles,
            long maxFileSizeBytes,
            int parseConcurrency,
            Duration parseTimeout
    ) {
        this.projectPath = Objects.requireNonNull(projectPath, "projectPath must not be null");
        this.ruleSet = ruleSet == null ? Set.of() : Collections.unmodifiableSet(new HashSet<>(ruleSet));
        this.mode = mode == null ? AnalyzeMode.FULL : mode;
        this.changedFiles = changedFiles == null ? Set.of() : Collections.unmodifiableSet(new HashSet<>(changedFiles));
        this.maxFileSizeBytes = maxFileSizeBytes > 0 ? maxFileSizeBytes : DEFAULT_MAX_FILE_SIZE_BYTES;
        this.parseConcurrency = parseConcurrency > 0 ? parseConcurrency : DEFAULT_PARSE_CONCURRENCY;
        this.parseTimeout = parseTimeout == null || parseTimeout.isZero() || parseTimeout.isNegative()
                ? DEFAULT_PARSE_TIMEOUT : parseTimeout;
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

    public long maxFileSizeBytes() {
        return maxFileSizeBytes;
    }

    public int parseConcurrency() {
        return parseConcurrency;
    }

    public Duration parseTimeout() {
        return parseTimeout;
    }
}

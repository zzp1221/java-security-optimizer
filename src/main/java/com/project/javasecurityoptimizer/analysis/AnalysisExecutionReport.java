package com.project.javasecurityoptimizer.analysis;

import java.util.List;

public record AnalysisExecutionReport(
        CacheStats cacheStats,
        List<String> degradedFiles,
        List<String> failedItems
) {
    public AnalysisExecutionReport {
        cacheStats = cacheStats == null ? CacheStats.empty() : cacheStats;
        degradedFiles = degradedFiles == null ? List.of() : List.copyOf(degradedFiles);
        failedItems = failedItems == null ? List.of() : List.copyOf(failedItems);
    }

    public static AnalysisExecutionReport empty() {
        return new AnalysisExecutionReport(CacheStats.empty(), List.of(), List.of());
    }
}

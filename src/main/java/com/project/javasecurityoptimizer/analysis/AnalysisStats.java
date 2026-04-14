package com.project.javasecurityoptimizer.analysis;

public record AnalysisStats(
        int indexedFiles,
        int parsedFiles,
        int skippedFiles,
        int issueCount,
        int ruleHitCount,
        int failedRuleCount
) {
}

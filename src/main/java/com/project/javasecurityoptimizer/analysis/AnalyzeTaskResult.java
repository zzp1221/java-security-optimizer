package com.project.javasecurityoptimizer.analysis;

import java.util.List;

public record AnalyzeTaskResult(
        List<AnalysisIssue> issues,
        AnalysisStats stats,
        long durationMillis,
        List<ProgressEvent> events,
        RuleExecutionMetrics ruleExecutionMetrics,
        AnalysisExecutionReport executionReport
) {
    public AnalyzeTaskResult {
        ruleExecutionMetrics = ruleExecutionMetrics == null
                ? new RuleExecutionMetrics(java.util.Map.of(), java.util.Map.of())
                : ruleExecutionMetrics;
        executionReport = executionReport == null ? AnalysisExecutionReport.empty() : executionReport;
    }
}

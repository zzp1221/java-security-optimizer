package com.project.javasecurityoptimizer.analysis;

import java.util.List;

public record AnalyzeTaskResult(
        List<AnalysisIssue> issues,
        AnalysisStats stats,
        long durationMillis,
        List<ProgressEvent> events
) {
}

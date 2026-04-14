package com.project.javasecurityoptimizer.task;

import com.project.javasecurityoptimizer.analysis.AnalyzeMode;

import java.util.List;
import java.util.Set;

public record TaskSubmitRequest(
        String taskId,
        String traceId,
        String workspaceId,
        String projectPath,
        AnalyzeMode mode,
        Set<String> ruleSet,
        List<String> changedFiles,
        Long maxFileSizeBytes,
        Integer parseConcurrency,
        Long parseTimeoutMillis
) {
}

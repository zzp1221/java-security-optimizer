package com.project.javasecurityoptimizer.task;

import com.project.javasecurityoptimizer.analysis.AnalyzeMode;

import java.util.List;
import java.util.Set;

public record TaskSubmitRequest(
        String taskId,
        String traceId,
        String workspaceId,
        String language,
        String projectPath,
        AnalyzeMode mode,
        Set<String> ruleSet,
        List<String> changedFiles,
        List<String> impactedFiles,
        Long maxFileSizeBytes,
        Long degradeFileSizeBytes,
        Integer parseConcurrency,
        Long parseTimeoutMillis,
        Integer parseRetryCount,
        Integer ruleConcurrency,
        Long ruleTimeoutMillis,
        Set<String> degradeRuleSet,
        Integer priority,
        Long taskTimeoutMillis,
        Integer maxRetries
) {
}

package com.project.javasecurityoptimizer.task;

import com.project.javasecurityoptimizer.analysis.AnalysisIssue;
import com.project.javasecurityoptimizer.analysis.ProgressEvent;
import com.project.javasecurityoptimizer.storage.TaskStatus;

import java.time.Instant;
import java.util.List;

public record TaskSnapshot(
        String taskId,
        String traceId,
        String workspaceId,
        TaskStatus status,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt,
        int issueCount,
        long durationMillis,
        String failureReason,
        TaskFailureCategory failureCategory,
        int attempt,
        int maxRetries,
        List<ProgressEvent> events,
        List<AnalysisIssue> issues
) {
}

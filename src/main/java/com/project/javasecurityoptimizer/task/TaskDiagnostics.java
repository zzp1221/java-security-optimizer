package com.project.javasecurityoptimizer.task;

import com.project.javasecurityoptimizer.storage.TaskStatus;

import java.time.Instant;
import java.util.List;

public record TaskDiagnostics(
        List<RecentTaskMetric> recentTasks,
        List<FailureTopReason> failureTopReasons
) {
    public record RecentTaskMetric(
            String taskId,
            TaskStatus status,
            long durationMillis,
            int issueCount,
            Instant finishedAt
    ) {
    }

    public record FailureTopReason(
            String reason,
            long count
    ) {
    }
}

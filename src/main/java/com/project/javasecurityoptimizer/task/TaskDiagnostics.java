package com.project.javasecurityoptimizer.task;

import com.project.javasecurityoptimizer.storage.TaskStatus;

import java.time.Instant;
import java.util.List;

public record TaskDiagnostics(
        List<RecentTaskMetric> recentTasks,
        List<FailureTopReason> failureTopReasons,
        List<DurationDistribution> durationDistributions,
        List<RuleHitTop> ruleHitTop
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

    public record DurationDistribution(
            String bucket,
            long count
    ) {
    }

    public record RuleHitTop(
            String ruleId,
            long hits
    ) {
    }
}

package com.project.javasecurityoptimizer.storage;

import com.project.javasecurityoptimizer.task.TaskFailureCategory;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;

public final class TaskRecord {
    private final String taskId;
    private final String workspaceId;
    private final String traceId;
    private TaskStatus status;
    private final Instant createdAt;
    private Instant startedAt;
    private Instant finishedAt;
    private Path reportPath;
    private int issueCount;
    private String failureReason;
    private TaskFailureCategory failureCategory;
    private long durationMillis;
    private int attempt;
    private int maxRetries;
    private boolean archived;

    public TaskRecord(
            String taskId,
            String workspaceId,
            String traceId,
            TaskStatus status,
            Instant createdAt,
            Instant startedAt,
            Instant finishedAt,
            Path reportPath,
            int issueCount,
            String failureReason,
            TaskFailureCategory failureCategory,
            long durationMillis,
            int attempt,
            int maxRetries,
            boolean archived
    ) {
        this.taskId = Objects.requireNonNull(taskId, "taskId must not be null");
        this.workspaceId = Objects.requireNonNull(workspaceId, "workspaceId must not be null");
        this.traceId = traceId == null || traceId.isBlank() ? taskId : traceId;
        this.status = status == null ? TaskStatus.CREATED : status;
        this.createdAt = createdAt == null ? Instant.now() : createdAt;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
        this.reportPath = reportPath;
        this.issueCount = Math.max(0, issueCount);
        this.failureReason = failureReason;
        this.failureCategory = failureCategory;
        this.durationMillis = Math.max(0, durationMillis);
        this.attempt = Math.max(0, attempt);
        this.maxRetries = Math.max(0, maxRetries);
        this.archived = archived;
    }

    public static TaskRecord create(String taskId, String workspaceId, String traceId, int maxRetries) {
        return new TaskRecord(
                taskId,
                workspaceId,
                traceId,
                TaskStatus.CREATED,
                Instant.now(),
                null,
                null,
                null,
                0,
                null,
                null,
                0,
                0,
                maxRetries,
                false
        );
    }

    public String taskId() {
        return taskId;
    }

    public String workspaceId() {
        return workspaceId;
    }

    public String traceId() {
        return traceId;
    }

    public TaskStatus status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public Instant finishedAt() {
        return finishedAt;
    }

    public Path reportPath() {
        return reportPath;
    }

    public int issueCount() {
        return issueCount;
    }

    public String failureReason() {
        return failureReason;
    }

    public long durationMillis() {
        return durationMillis;
    }

    public TaskFailureCategory failureCategory() {
        return failureCategory;
    }

    public int attempt() {
        return attempt;
    }

    public int maxRetries() {
        return maxRetries;
    }

    public boolean archived() {
        return archived;
    }

    public void markQueued() {
        this.status = TaskStatus.QUEUED;
    }

    public void markRunning(Instant startedAt) {
        this.status = TaskStatus.RUNNING;
        this.startedAt = startedAt == null ? Instant.now() : startedAt;
    }

    public void finishCompleted(Instant finishedAt, int issueCount, Path reportPath) {
        this.status = TaskStatus.COMPLETED;
        this.finishedAt = finishedAt == null ? Instant.now() : finishedAt;
        this.issueCount = Math.max(0, issueCount);
        this.reportPath = reportPath;
        this.failureReason = null;
        this.failureCategory = null;
        this.durationMillis = computeDurationMillis();
    }

    public void finishFailed(Instant finishedAt, String failureReason, TaskFailureCategory failureCategory) {
        this.status = TaskStatus.FAILED;
        this.finishedAt = finishedAt == null ? Instant.now() : finishedAt;
        this.failureReason = failureReason;
        this.failureCategory = failureCategory;
        this.durationMillis = computeDurationMillis();
    }

    public void finishCancelled(Instant finishedAt, String failureReason) {
        this.status = TaskStatus.CANCELLED;
        this.finishedAt = finishedAt == null ? Instant.now() : finishedAt;
        this.failureReason = failureReason;
        this.failureCategory = TaskFailureCategory.CANCELLED;
        this.durationMillis = computeDurationMillis();
    }

    public void markRetryScheduled() {
        this.attempt = this.attempt + 1;
        this.status = TaskStatus.QUEUED;
    }

    public void markArchived() {
        this.archived = true;
        this.status = TaskStatus.ARCHIVED;
    }

    public void clearReportPath() {
        this.reportPath = null;
    }

    private long computeDurationMillis() {
        if (startedAt == null || finishedAt == null) {
            return 0;
        }
        return Math.max(0, finishedAt.toEpochMilli() - startedAt.toEpochMilli());
    }
}

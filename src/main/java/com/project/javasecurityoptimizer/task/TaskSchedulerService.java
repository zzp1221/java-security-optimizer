package com.project.javasecurityoptimizer.task;

import com.project.javasecurityoptimizer.analysis.AnalyzeMode;
import com.project.javasecurityoptimizer.analysis.AnalyzeTaskRequest;
import com.project.javasecurityoptimizer.analysis.AnalyzeTaskResult;
import com.project.javasecurityoptimizer.analysis.JavaAnalysisEngine;
import com.project.javasecurityoptimizer.analysis.ProgressEvent;
import com.project.javasecurityoptimizer.storage.TaskRecord;
import com.project.javasecurityoptimizer.storage.TaskStateMachine;
import com.project.javasecurityoptimizer.storage.TaskStatus;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

@Service
public class TaskSchedulerService {
    private final JavaAnalysisEngine analysisEngine;
    private final Map<String, ManagedTask> tasks = new ConcurrentHashMap<>();
    private final LinkedBlockingQueue<String> queue = new LinkedBlockingQueue<>();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "analysis-task-worker");
        thread.setDaemon(true);
        return thread;
    });
    private volatile boolean running;

    public TaskSchedulerService(JavaAnalysisEngine analysisEngine) {
        this.analysisEngine = analysisEngine;
    }

    @PostConstruct
    public void start() {
        running = true;
        worker.submit(this::workerLoop);
    }

    @PreDestroy
    public void stop() {
        running = false;
        worker.shutdownNow();
    }

    public TaskSnapshot submit(TaskSubmitRequest request) {
        if (request == null || request.projectPath() == null || request.projectPath().isBlank()) {
            throw new IllegalArgumentException("projectPath must not be empty");
        }

        String taskId = request.taskId() == null || request.taskId().isBlank()
                ? "task-" + UUID.randomUUID()
                : request.taskId();
        String traceId = request.traceId() == null || request.traceId().isBlank() ? taskId : request.traceId();
        String workspaceId = request.workspaceId() == null || request.workspaceId().isBlank()
                ? "workspace-default"
                : request.workspaceId();

        AnalyzeTaskRequest analyzeTaskRequest = toAnalyzeTaskRequest(request);
        TaskRecord taskRecord = TaskRecord.create(taskId, workspaceId, traceId);
        ManagedTask task = new ManagedTask(taskRecord, analyzeTaskRequest);

        if (tasks.putIfAbsent(taskId, task) != null) {
            throw new IllegalArgumentException("taskId already exists: " + taskId);
        }

        synchronized (task) {
            task.events.add(ProgressEvent.of("queue", 0, "任务已创建"));
            TaskStateMachine.assertTransit(task.record.status(), TaskStatus.QUEUED);
            task.record.markQueued();
            task.events.add(ProgressEvent.of("queue", 1, "任务已入队"));
        }
        queue.offer(taskId);
        return snapshot(task);
    }

    public Optional<TaskSnapshot> findById(String taskId) {
        ManagedTask task = tasks.get(taskId);
        return task == null ? Optional.empty() : Optional.of(snapshot(task));
    }

    public boolean cancel(String taskId) {
        ManagedTask task = tasks.get(taskId);
        if (task == null) {
            return false;
        }

        synchronized (task) {
            if (task.record.status() == TaskStatus.QUEUED || task.record.status() == TaskStatus.CREATED) {
                queue.remove(taskId);
                if (TaskStateMachine.canTransit(task.record.status(), TaskStatus.CANCELLED)) {
                    task.record.finishCancelled(Instant.now(), "cancelled by user");
                    task.events.add(ProgressEvent.of("cancel", 100, "任务已取消"));
                    return true;
                }
            }

            if (task.record.status() == TaskStatus.RUNNING) {
                task.cancelRequested = true;
                TaskStateMachine.assertTransit(task.record.status(), TaskStatus.CANCELLED);
                task.record.finishCancelled(Instant.now(), "cancel requested during running");
                task.events.add(ProgressEvent.of("cancel", 100, "任务取消请求已接收"));
                return true;
            }
            return false;
        }
    }

    public TaskDiagnostics diagnostics() {
        List<TaskRecord> terminalTasks = tasks.values().stream()
                .map(managedTask -> managedTask.record)
                .filter(record -> record.finishedAt() != null)
                .toList();

        List<TaskDiagnostics.RecentTaskMetric> recentTasks = terminalTasks.stream()
                .sorted(Comparator.comparing(TaskRecord::finishedAt).reversed())
                .limit(10)
                .map(record -> new TaskDiagnostics.RecentTaskMetric(
                        record.taskId(),
                        record.status(),
                        record.durationMillis(),
                        record.issueCount(),
                        record.finishedAt()
                ))
                .toList();

        Map<String, Long> failureGroup = new LinkedHashMap<>();
        for (TaskRecord record : terminalTasks) {
            if (record.status() != TaskStatus.FAILED) {
                continue;
            }
            String reason = record.failureReason() == null || record.failureReason().isBlank()
                    ? "unknown"
                    : record.failureReason();
            failureGroup.put(reason, failureGroup.getOrDefault(reason, 0L) + 1);
        }

        List<TaskDiagnostics.FailureTopReason> failureTopReasons = failureGroup.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .map(entry -> new TaskDiagnostics.FailureTopReason(entry.getKey(), entry.getValue()))
                .toList();

        return new TaskDiagnostics(recentTasks, failureTopReasons);
    }

    private AnalyzeTaskRequest toAnalyzeTaskRequest(TaskSubmitRequest request) {
        Set<Path> changedFiles = request.changedFiles() == null
                ? Set.of()
                : request.changedFiles().stream().map(Path::of).collect(java.util.stream.Collectors.toSet());
        Duration parseTimeout = request.parseTimeoutMillis() == null
                ? null
                : Duration.ofMillis(Math.max(100, request.parseTimeoutMillis()));
        return new AnalyzeTaskRequest(
                Path.of(request.projectPath()),
                request.ruleSet(),
                request.mode() == null ? AnalyzeMode.FULL : request.mode(),
                changedFiles,
                request.maxFileSizeBytes() == null ? 0 : request.maxFileSizeBytes(),
                request.parseConcurrency() == null ? 0 : request.parseConcurrency(),
                parseTimeout
        );
    }

    private TaskSnapshot snapshot(ManagedTask task) {
        synchronized (task) {
            return new TaskSnapshot(
                    task.record.taskId(),
                    task.record.traceId(),
                    task.record.workspaceId(),
                    task.record.status(),
                    task.record.createdAt(),
                    task.record.startedAt(),
                    task.record.finishedAt(),
                    task.record.issueCount(),
                    task.record.durationMillis(),
                    task.record.failureReason(),
                    List.copyOf(task.events)
            );
        }
    }

    private void workerLoop() {
        while (running) {
            try {
                String taskId = queue.take();
                ManagedTask task = tasks.get(taskId);
                if (task == null) {
                    continue;
                }

                synchronized (task) {
                    if (task.record.status() != TaskStatus.QUEUED) {
                        continue;
                    }
                    TaskStateMachine.assertTransit(task.record.status(), TaskStatus.RUNNING);
                    task.record.markRunning(Instant.now());
                    task.events.add(ProgressEvent.of("run", 5, "任务开始执行"));
                }

                AnalyzeTaskResult result = analysisEngine.analyze(task.request);
                synchronized (task) {
                    if (task.record.status() == TaskStatus.CANCELLED || task.cancelRequested) {
                        if (task.record.status() != TaskStatus.CANCELLED
                                && TaskStateMachine.canTransit(task.record.status(), TaskStatus.CANCELLED)) {
                            task.record.finishCancelled(Instant.now(), "cancelled by user");
                            task.events.add(ProgressEvent.of("cancel", 100, "任务已取消"));
                        }
                        continue;
                    }

                    task.events.addAll(result.events());
                    TaskStateMachine.assertTransit(task.record.status(), TaskStatus.COMPLETED);
                    task.record.finishCompleted(Instant.now(), result.issues().size(), null);
                    task.events.add(ProgressEvent.of("report", 100, "任务完成"));
                }
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                // keep loop alive and attach failure to the latest running task if possible
                ManagedTask latestRunningTask = tasks.values().stream()
                        .filter(task -> task.record.status() == TaskStatus.RUNNING)
                        .findFirst()
                        .orElse(null);
                if (latestRunningTask != null) {
                    synchronized (latestRunningTask) {
                        if (TaskStateMachine.canTransit(latestRunningTask.record.status(), TaskStatus.FAILED)) {
                            latestRunningTask.record.finishFailed(Instant.now(), e.getMessage());
                            latestRunningTask.events.add(ProgressEvent.of("run", 100, "任务失败: " + e.getMessage()));
                        }
                    }
                }
            }
        }
    }

    private static final class ManagedTask {
        private final TaskRecord record;
        private final AnalyzeTaskRequest request;
        private final List<ProgressEvent> events = new ArrayList<>();
        private boolean cancelRequested;

        private ManagedTask(TaskRecord record, AnalyzeTaskRequest request) {
            this.record = record;
            this.request = request;
        }
    }
}

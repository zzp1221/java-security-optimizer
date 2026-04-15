package com.project.javasecurityoptimizer.task;

import com.project.javasecurityoptimizer.analysis.AnalyzeMode;
import com.project.javasecurityoptimizer.analysis.AnalyzeTaskRequest;
import com.project.javasecurityoptimizer.analysis.AnalyzeTaskResult;
import com.project.javasecurityoptimizer.analysis.AnalysisIssue;
import com.project.javasecurityoptimizer.analysis.ProgressEvent;
import com.project.javasecurityoptimizer.analysis.hint.ContextAwareJitHintService;
import com.project.javasecurityoptimizer.analysis.hint.ContextHintRequest;
import com.project.javasecurityoptimizer.analysis.hint.ContextHintResponse;
import com.project.javasecurityoptimizer.plugin.LanguagePlugin;
import com.project.javasecurityoptimizer.plugin.PluginException;
import com.project.javasecurityoptimizer.plugin.PluginHealthStatus;
import com.project.javasecurityoptimizer.plugin.PluginManagerService;
import com.project.javasecurityoptimizer.plugin.PluginRuntimeStatus;
import com.project.javasecurityoptimizer.security.SecurityAuditService;
import com.project.javasecurityoptimizer.storage.TaskRecord;
import com.project.javasecurityoptimizer.storage.TaskStateMachine;
import com.project.javasecurityoptimizer.storage.TaskStatus;
import org.springframework.beans.factory.annotation.Autowired;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class TaskSchedulerService {
    private static final Logger log = LoggerFactory.getLogger(TaskSchedulerService.class);
    private static final long DEFAULT_TASK_TIMEOUT_MILLIS = 5 * 60 * 1000L;
    private static final int DEFAULT_PRIORITY = 0;

    private final PluginManagerService pluginManagerService;
    private final SecurityAuditService securityAuditService;
    private final ContextAwareJitHintService contextAwareJitHintService;
    private final Map<String, ManagedTask> tasks = new ConcurrentHashMap<>();
    private final PriorityBlockingQueue<QueuedTask> queue = new PriorityBlockingQueue<>();
    private final AtomicLong queueSequence = new AtomicLong();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "analysis-task-worker");
        thread.setDaemon(true);
        return thread;
    });
    private final ExecutorService analysisExecutor = Executors.newCachedThreadPool(r -> {
        Thread thread = new Thread(r, "analysis-runner");
        thread.setDaemon(true);
        return thread;
    });
    private volatile boolean running;

    public TaskSchedulerService(PluginManagerService pluginManagerService, SecurityAuditService securityAuditService) {
        this(pluginManagerService, securityAuditService, new ContextAwareJitHintService());
    }

    @Autowired
    public TaskSchedulerService(
            PluginManagerService pluginManagerService,
            SecurityAuditService securityAuditService,
            ContextAwareJitHintService contextAwareJitHintService
    ) {
        this.pluginManagerService = pluginManagerService;
        this.securityAuditService = securityAuditService;
        this.contextAwareJitHintService = contextAwareJitHintService;
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
        analysisExecutor.shutdownNow();
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
        int maxRetries = request.maxRetries() == null ? 0 : Math.max(0, request.maxRetries());
        int priority = request.priority() == null ? DEFAULT_PRIORITY : Math.max(-100, Math.min(100, request.priority()));
        long taskTimeoutMillis = request.taskTimeoutMillis() == null
                ? DEFAULT_TASK_TIMEOUT_MILLIS
                : Math.max(500, request.taskTimeoutMillis());

        String language = pluginManagerService.normalizeLanguage(request.language());
        PluginHealthStatus pluginHealth = pluginManagerService.healthOf(language);
        if (pluginHealth.status() == PluginRuntimeStatus.UNAVAILABLE) {
            throw new IllegalArgumentException(pluginManagerService.unavailableHint(language));
        }
        AnalyzeTaskRequest analyzeTaskRequest = toAnalyzeTaskRequest(request);
        TaskRecord taskRecord = TaskRecord.create(taskId, workspaceId, traceId, maxRetries);
        LanguagePlugin plugin = pluginManagerService.resolve(language)
                .orElseThrow(() -> new IllegalArgumentException(pluginManagerService.unavailableHint(language)));
        ManagedTask task = new ManagedTask(taskRecord, analyzeTaskRequest, taskTimeoutMillis, priority, language, plugin);

        if (tasks.putIfAbsent(taskId, task) != null) {
            throw new IllegalArgumentException("taskId already exists: " + taskId);
        }

        synchronized (task) {
            task.events.add(ProgressEvent.of("queue", 0, "任务已创建"));
            if (pluginHealth.status() == PluginRuntimeStatus.DEGRADED) {
                task.events.add(ProgressEvent.of(
                        "plugin",
                        0,
                        "插件处于降级状态: " + pluginHealth.message() + "，任务将继续执行并按标准错误返回"
                ));
            } else {
                task.events.add(ProgressEvent.of("plugin", 0, "插件健康检查通过: " + pluginHealth.pluginId()));
            }
            TaskStateMachine.assertTransit(task.record.status(), TaskStatus.QUEUED);
            task.record.markQueued();
            task.events.add(ProgressEvent.of("queue", 1, "任务已入队"));
        }
        log.info("task submitted taskId={} traceId={} workspaceId={} timeoutMs={} maxRetries={}",
                taskId, traceId, workspaceId, taskTimeoutMillis, maxRetries);
        queue.offer(new QueuedTask(taskId, priority, queueSequence.incrementAndGet()));
        return snapshot(task);
    }

    public Optional<TaskSnapshot> findById(String taskId) {
        ManagedTask task = tasks.get(taskId);
        return task == null ? Optional.empty() : Optional.of(snapshot(task));
    }

    public Optional<ContextHintResponse> findJitHints(String taskId) {
        ManagedTask task = tasks.get(taskId);
        if (task == null) {
            return Optional.empty();
        }
        synchronized (task) {
            return Optional.ofNullable(task.jitHintResponse);
        }
    }

    public Optional<TaskSnapshot> retry(String taskId) {
        ManagedTask task = tasks.get(taskId);
        if (task == null) {
            return Optional.empty();
        }
        synchronized (task) {
            if (task.record.status() != TaskStatus.FAILED && task.record.status() != TaskStatus.CANCELLED) {
                return Optional.empty();
            }
            task.cancelRequested = false;
            task.events.add(ProgressEvent.of("retry", 1, "手动重试任务"));
            TaskStateMachine.assertTransit(task.record.status(), TaskStatus.QUEUED);
            task.record.markRetryScheduled();
            queue.offer(new QueuedTask(task.record.taskId(), task.priority, queueSequence.incrementAndGet()));
            log.info("task retry requested manually taskId={} traceId={} attempt={}/{}",
                    task.record.taskId(), task.record.traceId(), task.record.attempt(), task.record.maxRetries());
            return Optional.of(snapshot(task));
        }
    }

    public boolean cancel(String taskId) {
        ManagedTask task = tasks.get(taskId);
        if (task == null) {
            securityAuditService.recordTaskCancel(taskId, null, false, "task not found");
            return false;
        }

        synchronized (task) {
            if (task.record.status() == TaskStatus.QUEUED || task.record.status() == TaskStatus.CREATED) {
                queue.removeIf(queuedTask -> queuedTask.taskId().equals(taskId));
                if (TaskStateMachine.canTransit(task.record.status(), TaskStatus.CANCELLED)) {
                    task.record.finishCancelled(Instant.now(), "cancelled by user");
                    task.events.add(ProgressEvent.of("cancel", 100, "任务已取消"));
                    log.info("task cancelled from queue taskId={} traceId={}", task.record.taskId(), task.record.traceId());
                    securityAuditService.recordTaskCancel(task.record.taskId(), task.record.traceId(), true, "cancelled from queue");
                    return true;
                }
            }

            if (task.record.status() == TaskStatus.RUNNING) {
                task.cancelRequested = true;
                TaskStateMachine.assertTransit(task.record.status(), TaskStatus.CANCELLED);
                task.record.finishCancelled(Instant.now(), "cancel requested during running");
                task.events.add(ProgressEvent.of("cancel", 100, "任务取消请求已接收"));
                log.info("task cancel requested while running taskId={} traceId={}", task.record.taskId(), task.record.traceId());
                securityAuditService.recordTaskCancel(task.record.taskId(), task.record.traceId(), true, "cancel requested while running");
                return true;
            }
            securityAuditService.recordTaskCancel(task.record.taskId(), task.record.traceId(), false, "task state does not allow cancel");
            return false;
        }
    }

    public TaskDiagnostics diagnostics() {
        List<ManagedTask> allTasks = tasks.values().stream().toList();
        List<TaskRecord> terminalTasks = allTasks.stream()
                .map(task -> task.record)
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

        List<TaskDiagnostics.DurationDistribution> durationDistributions = buildDurationDistributions(terminalTasks);
        List<TaskDiagnostics.RuleHitTop> ruleHitTop = buildRuleHitTop(allTasks);

        return new TaskDiagnostics(recentTasks, failureTopReasons, durationDistributions, ruleHitTop);
    }

    public List<PluginHealthStatus> pluginHealth() {
        return pluginManagerService.allHealthStatus();
    }

    private AnalyzeTaskRequest toAnalyzeTaskRequest(TaskSubmitRequest request) {
        Set<Path> changedFiles = request.changedFiles() == null
                ? Set.of()
                : request.changedFiles().stream().map(Path::of).collect(java.util.stream.Collectors.toSet());
        Set<Path> impactedFiles = request.impactedFiles() == null
                ? Set.of()
                : request.impactedFiles().stream().map(Path::of).collect(java.util.stream.Collectors.toSet());
        Duration parseTimeout = request.parseTimeoutMillis() == null
                ? null
                : Duration.ofMillis(Math.max(100, request.parseTimeoutMillis()));
        Duration ruleTimeout = request.ruleTimeoutMillis() == null
                ? null
                : Duration.ofMillis(Math.max(50, request.ruleTimeoutMillis()));
        return new AnalyzeTaskRequest(
                Path.of(request.projectPath()),
                request.ruleSet(),
                request.mode() == null ? AnalyzeMode.FULL : request.mode(),
                changedFiles,
                impactedFiles,
                request.maxFileSizeBytes() == null ? 0 : request.maxFileSizeBytes(),
                request.degradeFileSizeBytes() == null ? 0 : request.degradeFileSizeBytes(),
                request.parseConcurrency() == null ? 0 : request.parseConcurrency(),
                parseTimeout,
                request.parseRetryCount() == null ? -1 : request.parseRetryCount(),
                request.ruleConcurrency() == null ? 0 : request.ruleConcurrency(),
                ruleTimeout,
                request.degradeRuleSet()
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
                    task.record.failureCategory(),
                    task.record.attempt(),
                    task.record.maxRetries(),
                    List.copyOf(task.events),
                    List.copyOf(task.issues)
            );
        }
    }

    private void workerLoop() {
        while (running) {
            try {
                QueuedTask queuedTask = queue.take();
                ManagedTask task = tasks.get(queuedTask.taskId());
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
                    log.info("task started taskId={} traceId={} attempt={}/{}",
                            task.record.taskId(), task.record.traceId(), task.record.attempt(), task.record.maxRetries());
                }
                runManagedTask(task);
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("worker loop crashed: {}", e.getMessage(), e);
                ManagedTask latestRunningTask = tasks.values().stream()
                        .filter(task -> task.record.status() == TaskStatus.RUNNING)
                        .findFirst()
                        .orElse(null);
                if (latestRunningTask != null) {
                    synchronized (latestRunningTask) {
                        if (TaskStateMachine.canTransit(latestRunningTask.record.status(), TaskStatus.FAILED)) {
                            latestRunningTask.record.finishFailed(Instant.now(), rootCauseMessage(e), TaskFailureCategory.SYSTEM_ERROR);
                            latestRunningTask.events.add(ProgressEvent.of("run", 100, "任务失败: " + e.getMessage()));
                            log.error("task failed due to system error taskId={} traceId={} reason={}",
                                    latestRunningTask.record.taskId(),
                                    latestRunningTask.record.traceId(),
                                    rootCauseMessage(e));
                        }
                    }
                }
            }
        }
    }

    private void runManagedTask(ManagedTask task) {
        Future<AnalyzeTaskResult> future = analysisExecutor.submit(() -> task.plugin.analyze(task.request));
        try {
            AnalyzeTaskResult result = future.get(task.taskTimeoutMillis, TimeUnit.MILLISECONDS);
            ContextHintResponse jitHintResponse = generateJitHints(task);
            synchronized (task) {
                if (task.record.status() == TaskStatus.CANCELLED || task.cancelRequested) {
                    if (task.record.status() != TaskStatus.CANCELLED
                            && TaskStateMachine.canTransit(task.record.status(), TaskStatus.CANCELLED)) {
                        task.record.finishCancelled(Instant.now(), "cancelled by user");
                        task.events.add(ProgressEvent.of("cancel", 100, "任务已取消"));
                    }
                    return;
                }

                task.events.addAll(result.events());
                task.issues.clear();
                task.issues.addAll(result.issues());
                for (AnalysisIssue issue : result.issues()) {
                    task.ruleHitCounters.put(issue.ruleId(), task.ruleHitCounters.getOrDefault(issue.ruleId(), 0L) + 1L);
                }
                task.jitHintResponse = jitHintResponse;
                if (jitHintResponse != null) {
                    task.events.add(ProgressEvent.of(
                            "hint",
                            95,
                            "已生成多级上下文与JIT提示，条数: " + jitHintResponse.jitHints().size()
                    ));
                }
                TaskStateMachine.assertTransit(task.record.status(), TaskStatus.COMPLETED);
                task.record.finishCompleted(Instant.now(), result.issues().size(), null);
                task.events.add(ProgressEvent.of("report", 100, "任务完成"));
                log.info("task completed taskId={} traceId={} durationMs={} issueCount={}",
                        task.record.taskId(), task.record.traceId(), task.record.durationMillis(), task.record.issueCount());
            }
        } catch (TimeoutException timeoutException) {
            future.cancel(true);
            handleTaskError(task, TaskFailureCategory.TIMEOUT, "task timeout after " + task.taskTimeoutMillis + "ms");
        } catch (Exception e) {
            future.cancel(true);
            Throwable root = rootCause(e);
            if (root instanceof PluginException pluginException) {
                handleTaskError(
                        task,
                        TaskFailureCategory.PLUGIN_UNAVAILABLE,
                        "plugin " + task.language + " unavailable: "
                                + pluginException.errorCode() + ", " + pluginException.getMessage()
                );
                return;
            }
            handleTaskError(task, TaskFailureCategory.ANALYSIS_ERROR, rootCauseMessage(e));
        }
    }

    private ContextHintResponse generateJitHints(ManagedTask task) {
        if (!"java".equalsIgnoreCase(task.language)) {
            return null;
        }
        try {
            List<String> targetFiles = List.of();
            if (task.request.mode() == AnalyzeMode.INCREMENTAL) {
                List<String> combined = new ArrayList<>();
                for (Path path : task.request.changedFiles()) {
                    combined.add(path.toString());
                }
                for (Path path : task.request.impactedFiles()) {
                    combined.add(path.toString());
                }
                targetFiles = combined;
            }
            ContextHintRequest hintRequest = new ContextHintRequest(
                    task.request.projectPath().toString(),
                    targetFiles,
                    100,
                    40
            );
            return contextAwareJitHintService.analyze(hintRequest);
        } catch (Exception e) {
            synchronized (task) {
                task.events.add(ProgressEvent.of("hint", 95, "JIT提示生成失败: " + rootCauseMessage(e)));
            }
            return null;
        }
    }

    private void handleTaskError(ManagedTask task, TaskFailureCategory failureCategory, String reason) {
        synchronized (task) {
            if (task.record.status() == TaskStatus.CANCELLED || task.cancelRequested) {
                return;
            }
            if (task.record.attempt() < task.record.maxRetries()) {
                TaskStateMachine.assertTransit(task.record.status(), TaskStatus.QUEUED);
                task.record.markRetryScheduled();
                task.events.add(ProgressEvent.of("retry", 2, "任务重试中，原因: " + reason));
                queue.offer(new QueuedTask(task.record.taskId(), task.priority, queueSequence.incrementAndGet()));
                log.warn("task retry scheduled taskId={} traceId={} attempt={}/{} reason={}",
                        task.record.taskId(),
                        task.record.traceId(),
                        task.record.attempt(),
                        task.record.maxRetries(),
                        reason);
                return;
            }
            if (TaskStateMachine.canTransit(task.record.status(), TaskStatus.FAILED)) {
                task.record.finishFailed(Instant.now(), reason, failureCategory);
                task.events.add(ProgressEvent.of("run", 100, "任务失败: " + reason));
                log.error("task failed taskId={} traceId={} category={} reason={}",
                        task.record.taskId(), task.record.traceId(), failureCategory, reason);
            }
        }
    }

    private List<TaskDiagnostics.DurationDistribution> buildDurationDistributions(List<TaskRecord> terminalTasks) {
        Map<String, Long> buckets = new LinkedHashMap<>();
        buckets.put("<5s", 0L);
        buckets.put("5s-30s", 0L);
        buckets.put("30s-120s", 0L);
        buckets.put(">=120s", 0L);

        for (TaskRecord record : terminalTasks) {
            long millis = record.durationMillis();
            String bucket = millis < 5_000 ? "<5s"
                    : millis < 30_000 ? "5s-30s"
                    : millis < 120_000 ? "30s-120s"
                    : ">=120s";
            buckets.put(bucket, buckets.getOrDefault(bucket, 0L) + 1L);
        }

        return buckets.entrySet().stream()
                .map(entry -> new TaskDiagnostics.DurationDistribution(entry.getKey(), entry.getValue()))
                .toList();
    }

    private List<TaskDiagnostics.RuleHitTop> buildRuleHitTop(List<ManagedTask> allTasks) {
        Map<String, Long> merged = new HashMap<>();
        for (ManagedTask task : allTasks) {
            if (task.record.status() != TaskStatus.COMPLETED) {
                continue;
            }
            for (Map.Entry<String, Long> entry : task.ruleHitCounters.entrySet()) {
                merged.put(entry.getKey(), merged.getOrDefault(entry.getKey(), 0L) + entry.getValue());
            }
        }
        return merged.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .map(entry -> new TaskDiagnostics.RuleHitTop(entry.getKey(), entry.getValue()))
                .toList();
    }

    private String rootCauseMessage(Throwable throwable) {
        Throwable current = rootCause(throwable);
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static final class ManagedTask {
        private final TaskRecord record;
        private final AnalyzeTaskRequest request;
        private final long taskTimeoutMillis;
        private final int priority;
        private final String language;
        private final LanguagePlugin plugin;
        private final List<ProgressEvent> events = new ArrayList<>();
        private final List<AnalysisIssue> issues = new ArrayList<>();
        private final Map<String, Long> ruleHitCounters = new HashMap<>();
        private ContextHintResponse jitHintResponse;
        private boolean cancelRequested;

        private ManagedTask(
                TaskRecord record,
                AnalyzeTaskRequest request,
                long taskTimeoutMillis,
                int priority,
                String language,
                LanguagePlugin plugin
        ) {
            this.record = record;
            this.request = request;
            this.taskTimeoutMillis = taskTimeoutMillis;
            this.priority = priority;
            this.language = language;
            this.plugin = plugin;
        }
    }

    private record QueuedTask(String taskId, int priority, long sequence) implements Comparable<QueuedTask> {
        @Override
        public int compareTo(QueuedTask other) {
            int priorityCompare = Integer.compare(other.priority, this.priority);
            if (priorityCompare != 0) {
                return priorityCompare;
            }
            return Long.compare(this.sequence, other.sequence);
        }
    }
}

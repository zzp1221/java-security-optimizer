package com.project.javasecurityoptimizer.task;

import com.project.javasecurityoptimizer.analysis.AnalysisExecutionReport;
import com.project.javasecurityoptimizer.analysis.AnalysisIssue;
import com.project.javasecurityoptimizer.analysis.AnalysisStats;
import com.project.javasecurityoptimizer.analysis.AnalyzeTaskRequest;
import com.project.javasecurityoptimizer.analysis.AnalyzeTaskResult;
import com.project.javasecurityoptimizer.analysis.FixCandidate;
import com.project.javasecurityoptimizer.analysis.FixSafetyLevel;
import com.project.javasecurityoptimizer.analysis.IssueSeverity;
import com.project.javasecurityoptimizer.analysis.JavaAnalysisEngine;
import com.project.javasecurityoptimizer.analysis.ProgressEvent;
import com.project.javasecurityoptimizer.analysis.RuleExecutionMetrics;
import com.project.javasecurityoptimizer.analysis.hint.ContextHintResponse;
import com.project.javasecurityoptimizer.plugin.PluginManagerService;
import com.project.javasecurityoptimizer.security.SecurityAuditService;
import com.project.javasecurityoptimizer.storage.TaskStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskSchedulerServiceE2ETest {
    private TaskSchedulerService schedulerService;

    @AfterEach
    void tearDown() {
        if (schedulerService != null) {
            schedulerService.stop();
        }
    }

    @Test
    void shouldRunTaskToCompletionAndExposeDiagnostics() throws Exception {
        schedulerService = new TaskSchedulerService(
                new PluginManagerService(new DeterministicEngine(false), "1.0.0"),
                SecurityAuditService.noop()
        );
        schedulerService.start();

        Path projectDir = Files.createTempDirectory("task-e2e-project");
        Files.writeString(projectDir.resolve("Demo.java"), """
                class Demo {
                    void run() {
                        String a = "1";
                        String b = "2";
                        boolean same = a == b;
                    }
                }
                """);

        TaskSnapshot submitted = schedulerService.submit(new TaskSubmitRequest(
                "task-e2e-001",
                "trace-e2e-001",
                "ws-e2e",
                "java",
                projectDir.toAbsolutePath().normalize().toString(),
                com.project.javasecurityoptimizer.analysis.AnalyzeMode.FULL,
                Set.of("JAVA.STRING.EQUALITY"),
                List.of(),
                List.of(),
                1024L * 1024,
                512L * 1024,
                1,
                500L,
                1,
                1,
                300L,
                Set.of(),
                5,
                2000L,
                0
        ));
        assertEquals(TaskStatus.QUEUED, submitted.status());

        TaskSnapshot done = waitForTerminalStatus("task-e2e-001", Duration.ofSeconds(5));
        assertEquals(TaskStatus.COMPLETED, done.status());
        assertFalse(done.issues().isEmpty());
        assertTrue(done.events().stream().anyMatch(event -> "report".equals(event.stage())));
        ContextHintResponse jitHints = schedulerService.findJitHints("task-e2e-001").orElse(null);
        assertNotNull(jitHints);
        assertFalse(jitHints.fileSummaries().isEmpty());

        TaskDiagnostics diagnostics = schedulerService.diagnostics();
        assertFalse(diagnostics.durationDistributions().isEmpty());
        assertFalse(diagnostics.ruleHitTop().isEmpty());
        assertTrue(diagnostics.ruleHitTop().stream().anyMatch(top -> top.ruleId().equals("JAVA.STRING.EQUALITY")));
    }

    @Test
    void shouldRetryOnceAndFailWithTimeoutCategory() throws Exception {
        schedulerService = new TaskSchedulerService(
                new PluginManagerService(new DeterministicEngine(true), "1.0.0"),
                SecurityAuditService.noop()
        );
        schedulerService.start();

        schedulerService.submit(new TaskSubmitRequest(
                "task-e2e-timeout",
                "trace-e2e-timeout",
                "ws-e2e",
                "java",
                Path.of(".").toAbsolutePath().normalize().toString(),
                com.project.javasecurityoptimizer.analysis.AnalyzeMode.FULL,
                Set.of(),
                List.of(),
                List.of(),
                1024L * 1024,
                512L * 1024,
                1,
                500L,
                1,
                1,
                300L,
                Set.of(),
                1,
                500L,
                1
        ));

        TaskSnapshot failed = waitForTerminalStatus("task-e2e-timeout", Duration.ofSeconds(8));
        assertEquals(TaskStatus.FAILED, failed.status());
        assertEquals(TaskFailureCategory.TIMEOUT, failed.failureCategory());
        assertNotNull(failed.failureReason());
        assertEquals(1, failed.attempt());
        assertTrue(failed.events().stream().anyMatch(event -> "retry".equals(event.stage())));
    }

    private TaskSnapshot waitForTerminalStatus(String taskId, Duration timeout) throws Exception {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadline) {
            TaskSnapshot snapshot = schedulerService.findById(taskId).orElseThrow();
            if (snapshot.status() == TaskStatus.COMPLETED
                    || snapshot.status() == TaskStatus.FAILED
                    || snapshot.status() == TaskStatus.CANCELLED) {
                return snapshot;
            }
            Thread.sleep(80);
        }
        throw new IllegalStateException("task did not reach terminal status: " + taskId);
    }

    private static final class DeterministicEngine extends JavaAnalysisEngine {
        private final boolean slow;
        private final AtomicInteger callCount = new AtomicInteger();

        private DeterministicEngine(boolean slow) {
            this.slow = slow;
        }

        @Override
        public AnalyzeTaskResult analyze(AnalyzeTaskRequest request) {
            if (slow) {
                try {
                    Thread.sleep(1200);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                }
            }
            int call = callCount.incrementAndGet();
            List<AnalysisIssue> issues = List.of(
                    new AnalysisIssue(
                            "JAVA.STRING.EQUALITY",
                            "string should use equals",
                            request.projectPath().resolve("Sample.java"),
                            1,
                            IssueSeverity.MEDIUM,
                            List.of(new FixCandidate("Replace ==", "Use Objects.equals(a, b)", FixSafetyLevel.SAFE))
                    )
            );
            List<ProgressEvent> events = List.of(
                    ProgressEvent.of("index", 20, "index done"),
                    ProgressEvent.of("parse", 40, "parse done"),
                    ProgressEvent.of("analyze", 80, "analyze done"),
                    ProgressEvent.of("report", 100, "report done #" + call)
            );
            return new AnalyzeTaskResult(
                    issues,
                    new AnalysisStats(1, 1, 0, issues.size(), issues.size(), 0),
                    50,
                    events,
                    new RuleExecutionMetrics(Map.of("JAVA.STRING.EQUALITY", issues.size()), Map.of("JAVA.STRING.EQUALITY", 20L)),
                    AnalysisExecutionReport.empty()
            );
        }
    }
}

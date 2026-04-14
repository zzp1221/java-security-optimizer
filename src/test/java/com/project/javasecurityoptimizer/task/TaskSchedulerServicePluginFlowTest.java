package com.project.javasecurityoptimizer.task;

import com.project.javasecurityoptimizer.analysis.AnalyzeMode;
import com.project.javasecurityoptimizer.analysis.JavaAnalysisEngine;
import com.project.javasecurityoptimizer.plugin.PluginManagerService;
import com.project.javasecurityoptimizer.security.SecurityAuditService;
import com.project.javasecurityoptimizer.storage.TaskStatus;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskSchedulerServicePluginFlowTest {
    @Test
    void shouldFailFastWhenLanguagePluginNotFound() throws Exception {
        TaskSchedulerService service = new TaskSchedulerService(
                new PluginManagerService(new JavaAnalysisEngine(), "1.0.0"),
                SecurityAuditService.noop()
        );
        service.start();
        try {
            Path projectDir = Files.createTempDirectory("task-plugin-missing");
            TaskSubmitRequest request = buildRequest("python", projectDir.toString());
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> service.submit(request));
            assertTrue(exception.getMessage().contains("plugin unavailable"));
        } finally {
            service.stop();
        }
    }

    @Test
    void shouldMarkCppPlaceholderTaskAsPluginUnavailable() throws Exception {
        TaskSchedulerService service = new TaskSchedulerService(
                new PluginManagerService(new JavaAnalysisEngine(), "1.0.0"),
                SecurityAuditService.noop()
        );
        service.start();
        try {
            Path projectDir = Files.createTempDirectory("task-plugin-cpp");
            TaskSnapshot snapshot = service.submit(buildRequest("cpp", projectDir.toString()));

            Instant deadline = Instant.now().plus(Duration.ofSeconds(3));
            TaskSnapshot latest = snapshot;
            while (Instant.now().isBefore(deadline)) {
                latest = service.findById(snapshot.taskId()).orElse(latest);
                if (latest.status() == TaskStatus.FAILED || latest.status() == TaskStatus.COMPLETED) {
                    break;
                }
                Thread.sleep(50L);
            }

            assertEquals(TaskStatus.FAILED, latest.status());
            assertEquals(TaskFailureCategory.PLUGIN_UNAVAILABLE, latest.failureCategory());
            assertTrue(latest.failureReason().contains("NOT_IMPLEMENTED"));
        } finally {
            service.stop();
        }
    }

    private TaskSubmitRequest buildRequest(String language, String projectPath) {
        return new TaskSubmitRequest(
                null,
                null,
                null,
                language,
                projectPath,
                AnalyzeMode.FULL,
                Set.of(),
                List.of(),
                List.of(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }
}

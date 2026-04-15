package com.project.javasecurityoptimizer.task;

import com.project.javasecurityoptimizer.analysis.hint.ContextHintResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/tasks")
public class TaskController {
    private final TaskSchedulerService taskSchedulerService;

    public TaskController(TaskSchedulerService taskSchedulerService) {
        this.taskSchedulerService = taskSchedulerService;
    }

    @PostMapping
    public TaskSnapshot submitTask(@RequestBody TaskSubmitRequest request) {
        try {
            return taskSchedulerService.submit(request);
        } catch (IllegalArgumentException illegalArgumentException) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, illegalArgumentException.getMessage(), illegalArgumentException);
        }
    }

    @GetMapping("/{taskId}")
    public TaskSnapshot getTask(@PathVariable String taskId) {
        return taskSchedulerService.findById(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "task not found: " + taskId));
    }

    @GetMapping("/{taskId}/jit-hints")
    public ContextHintResponse getJitHints(@PathVariable String taskId) {
        return taskSchedulerService.findJitHints(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "jit hints not found for task: " + taskId));
    }

    @PostMapping("/{taskId}/cancel")
    public Map<String, Object> cancelTask(@PathVariable String taskId) {
        boolean cancelled = taskSchedulerService.cancel(taskId);
        if (!cancelled) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "task can not be cancelled: " + taskId);
        }
        return Map.of("cancelled", true, "taskId", taskId);
    }

    @PostMapping("/{taskId}/retry")
    public TaskSnapshot retryTask(@PathVariable String taskId) {
        return taskSchedulerService.retry(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "task can not be retried: " + taskId));
    }

    @GetMapping("/diagnostics")
    public TaskDiagnostics diagnostics() {
        return taskSchedulerService.diagnostics();
    }

    @GetMapping("/plugins/health")
    public List<Map<String, Object>> pluginHealth() {
        return taskSchedulerService.pluginHealth().stream()
                .map(status -> Map.<String, Object>of(
                        "language", status.language(),
                        "pluginId", status.pluginId(),
                        "status", status.status().name(),
                        "implemented", status.implemented(),
                        "compatible", status.compatible(),
                        "supportsAutofix", status.supportsAutofix(),
                        "message", status.message()
                ))
                .toList();
    }
}

package com.project.javasecurityoptimizer.task;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

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

    @PostMapping("/{taskId}/cancel")
    public Map<String, Object> cancelTask(@PathVariable String taskId) {
        boolean cancelled = taskSchedulerService.cancel(taskId);
        if (!cancelled) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "task can not be cancelled: " + taskId);
        }
        return Map.of("cancelled", true, "taskId", taskId);
    }

    @GetMapping("/diagnostics")
    public TaskDiagnostics diagnostics() {
        return taskSchedulerService.diagnostics();
    }
}

package com.project.javasecurityoptimizer.security;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/security/audit")
public class SecurityAuditController {
    private final SecurityAuditService securityAuditService;

    public SecurityAuditController(SecurityAuditService securityAuditService) {
        this.securityAuditService = securityAuditService;
    }

    @PostMapping("/fix-apply")
    public Map<String, Object> recordFixApply(@RequestBody FixApplyAuditRequest request) {
        if (request == null || request.taskId() == null || request.taskId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "taskId must not be empty");
        }
        if (request.fixId() == null || request.fixId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "fixId must not be empty");
        }
        if (!Boolean.TRUE.equals(request.confirmed())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "fix apply operation requires explicit confirmation");
        }
        securityAuditService.recordFixApply(
                request.taskId(),
                request.rulePackId(),
                request.fixId(),
                request.operator(),
                true,
                "fix apply confirmed"
        );
        return Map.of(
                "accepted", true,
                "taskId", request.taskId(),
                "fixId", request.fixId()
        );
    }

    @GetMapping("/events")
    public List<SecurityAuditEvent> recentEvents(@RequestParam(defaultValue = "50") int limit) {
        return securityAuditService.recentEvents(limit);
    }
}

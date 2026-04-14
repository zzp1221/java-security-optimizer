package com.project.javasecurityoptimizer.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class SecurityAuditService {
    private static final Logger log = LoggerFactory.getLogger("security-audit");
    private static final int MAX_RECENT_EVENTS = 200;

    private static final SecurityAuditService NOOP = new SecurityAuditService(true);

    private final boolean noop;
    private final Deque<SecurityAuditEvent> recentEvents = new ArrayDeque<>();

    public SecurityAuditService() {
        this(false);
    }

    private SecurityAuditService(boolean noop) {
        this.noop = noop;
    }

    public static SecurityAuditService noop() {
        return NOOP;
    }

    public void recordRulePackImport(String rulePackId, boolean success, String message) {
        recordEvent(new SecurityAuditEvent(
                null,
                rulePackId,
                SecurityAuditAction.RULE_PACK_IMPORT.name(),
                Instant.now(),
                success,
                message,
                Map.of()
        ));
    }

    public void recordTaskCancel(String taskId, String traceId, boolean success, String message) {
        recordEvent(new SecurityAuditEvent(
                taskId,
                null,
                SecurityAuditAction.TASK_CANCEL.name(),
                Instant.now(),
                success,
                message,
                traceId == null ? Map.of() : Map.of("traceId", traceId)
        ));
    }

    public void recordFixApply(String taskId, String rulePackId, String fixId, String operator, boolean success, String message) {
        recordEvent(new SecurityAuditEvent(
                taskId,
                rulePackId,
                SecurityAuditAction.FIX_APPLY.name(),
                Instant.now(),
                success,
                message,
                Map.of(
                        "fixId", Objects.requireNonNullElse(fixId, ""),
                        "operator", Objects.requireNonNullElse(operator, "unknown")
                )
        ));
    }

    public synchronized List<SecurityAuditEvent> recentEvents(int limit) {
        int size = Math.max(1, Math.min(limit, MAX_RECENT_EVENTS));
        return recentEvents.stream().limit(size).toList();
    }

    private void recordEvent(SecurityAuditEvent event) {
        if (noop) {
            return;
        }
        String line = String.format(
                "taskId=%s rulePackId=%s userAction=%s timestamp=%s success=%s message=%s metadata=%s",
                nullToDash(event.taskId()),
                nullToDash(event.rulePackId()),
                event.userAction(),
                event.timestamp(),
                event.success(),
                nullToDash(event.message()),
                event.metadata()
        );
        log.info(line);
        appendRecent(event);
    }

    private synchronized void appendRecent(SecurityAuditEvent event) {
        recentEvents.addFirst(event);
        while (recentEvents.size() > MAX_RECENT_EVENTS) {
            recentEvents.removeLast();
        }
    }

    private String nullToDash(String value) {
        return value == null ? "-" : value;
    }
}

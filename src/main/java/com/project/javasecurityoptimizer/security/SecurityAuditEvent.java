package com.project.javasecurityoptimizer.security;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public record SecurityAuditEvent(
        String taskId,
        String rulePackId,
        String userAction,
        Instant timestamp,
        boolean success,
        String message,
        Map<String, String> metadata
) {
    public SecurityAuditEvent {
        Objects.requireNonNull(userAction, "userAction must not be null");
        Objects.requireNonNull(timestamp, "timestamp must not be null");
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}

package com.project.javasecurityoptimizer.rulepack;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record RulePackAuditEvent(
        String eventId,
        Instant timestamp,
        RulePackAuditAction action,
        String operator,
        String workspaceId,
        String packId,
        String version,
        String detail
) {
    public RulePackAuditEvent {
        eventId = eventId == null || eventId.isBlank() ? "audit-" + UUID.randomUUID() : eventId;
        timestamp = timestamp == null ? Instant.now() : timestamp;
        Objects.requireNonNull(action, "action must not be null");
        operator = operator == null ? "system" : operator;
        packId = packId == null ? "" : packId;
        version = version == null ? "" : version;
        detail = detail == null ? "" : detail;
    }
}

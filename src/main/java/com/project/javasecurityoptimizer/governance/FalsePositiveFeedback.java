package com.project.javasecurityoptimizer.governance;

import java.time.Instant;
import java.util.Objects;

public record FalsePositiveFeedback(
        String workspaceId,
        String ruleId,
        String issueFingerprint,
        String comment,
        Instant timestamp
) {
    public FalsePositiveFeedback {
        Objects.requireNonNull(workspaceId, "workspaceId must not be null");
        Objects.requireNonNull(ruleId, "ruleId must not be null");
        Objects.requireNonNull(issueFingerprint, "issueFingerprint must not be null");
        comment = comment == null ? "" : comment;
        timestamp = timestamp == null ? Instant.now() : timestamp;
    }
}

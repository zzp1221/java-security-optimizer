package com.project.javasecurityoptimizer.rulepack;

import java.time.Instant;
import java.util.Objects;

public record ProjectRulePackBinding(
        String workspaceId,
        String packId,
        String version,
        ReleaseEnvironment lockedEnvironment,
        Instant boundAt,
        String boundBy
) {
    public ProjectRulePackBinding {
        Objects.requireNonNull(workspaceId, "workspaceId must not be null");
        Objects.requireNonNull(packId, "packId must not be null");
        Objects.requireNonNull(version, "version must not be null");
        Objects.requireNonNull(lockedEnvironment, "lockedEnvironment must not be null");
        boundAt = boundAt == null ? Instant.now() : boundAt;
        boundBy = boundBy == null ? "system" : boundBy;
    }
}

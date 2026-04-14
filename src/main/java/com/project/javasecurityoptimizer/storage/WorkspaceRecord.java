package com.project.javasecurityoptimizer.storage;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;

public record WorkspaceRecord(
        String workspaceId,
        String name,
        Path rootPath,
        Instant createdAt,
        Instant lastAccessedAt
) {
    public WorkspaceRecord {
        Objects.requireNonNull(workspaceId, "workspaceId must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(rootPath, "rootPath must not be null");
        createdAt = createdAt == null ? Instant.now() : createdAt;
        lastAccessedAt = lastAccessedAt == null ? createdAt : lastAccessedAt;
    }
}

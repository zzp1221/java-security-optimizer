package com.project.javasecurityoptimizer.autofix;

import java.time.Instant;
import java.util.List;

public record AutoFixAuditEntry(
        String operationId,
        String action,
        String operator,
        boolean success,
        String message,
        List<String> touchedFiles,
        Instant timestamp
) {
    public AutoFixAuditEntry {
        touchedFiles = touchedFiles == null ? List.of() : List.copyOf(touchedFiles);
        timestamp = timestamp == null ? Instant.now() : timestamp;
    }
}

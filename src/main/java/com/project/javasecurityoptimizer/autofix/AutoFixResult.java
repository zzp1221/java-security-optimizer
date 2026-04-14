package com.project.javasecurityoptimizer.autofix;

import java.util.List;

public record AutoFixResult(
        String operationId,
        boolean applied,
        String rollbackId,
        List<PatchConflict> conflicts,
        List<PatchPreview> previews,
        String qualityGateMessage
) {
    public AutoFixResult {
        conflicts = conflicts == null ? List.of() : List.copyOf(conflicts);
        previews = previews == null ? List.of() : List.copyOf(previews);
    }
}

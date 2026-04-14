package com.project.javasecurityoptimizer.autofix;

import java.util.List;

public record PatchPlan(
        List<PatchEdit> edits,
        List<PatchConflict> conflicts
) {
    public PatchPlan {
        edits = edits == null ? List.of() : List.copyOf(edits);
        conflicts = conflicts == null ? List.of() : List.copyOf(conflicts);
    }

    public boolean hasConflicts() {
        return !conflicts.isEmpty();
    }
}

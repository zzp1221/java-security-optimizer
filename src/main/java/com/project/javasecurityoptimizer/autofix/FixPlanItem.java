package com.project.javasecurityoptimizer.autofix;

import com.project.javasecurityoptimizer.analysis.AnalysisIssue;
import com.project.javasecurityoptimizer.analysis.FixCandidate;

import java.util.List;
import java.util.Objects;

public record FixPlanItem(
        AnalysisIssue issue,
        FixCandidate candidate,
        List<PatchEdit> edits
) {
    public FixPlanItem {
        Objects.requireNonNull(issue, "issue must not be null");
        Objects.requireNonNull(candidate, "candidate must not be null");
        edits = edits == null ? List.of() : List.copyOf(edits);
    }
}

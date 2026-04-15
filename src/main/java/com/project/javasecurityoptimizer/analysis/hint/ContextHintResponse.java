package com.project.javasecurityoptimizer.analysis.hint;

import java.util.List;

public record ContextHintResponse(
        ProjectContextSummary projectSummary,
        List<FileContextSummary> fileSummaries,
        List<JitHint> jitHints
) {
    public ContextHintResponse {
        fileSummaries = fileSummaries == null ? List.of() : List.copyOf(fileSummaries);
        jitHints = jitHints == null ? List.of() : List.copyOf(jitHints);
    }
}

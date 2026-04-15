package com.project.javasecurityoptimizer.analysis.hint;

public record ProjectContextSummary(
        int fileCount,
        int classCount,
        int methodCount,
        int loopCount,
        int branchCount
) {
}

package com.project.javasecurityoptimizer.analysis.hint;

public record ClassContextSummary(
        String className,
        int methodCount,
        int fieldCount,
        int constructorCount
) {
}

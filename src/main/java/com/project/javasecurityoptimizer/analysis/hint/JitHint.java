package com.project.javasecurityoptimizer.analysis.hint;

public record JitHint(
        String hintId,
        String level,
        String filePath,
        String className,
        String methodName,
        String message,
        String recommendation,
        String priority
) {
}

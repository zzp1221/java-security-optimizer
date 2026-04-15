package com.project.javasecurityoptimizer.analysis.hint;

public record MethodContextSummary(
        String methodName,
        int statementCount,
        int loopCount,
        int branchCount,
        int callCount,
        boolean synchronizedMethod
) {
}

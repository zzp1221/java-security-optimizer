package com.project.javasecurityoptimizer.autofix;

public record FixApplyStrategy(
        boolean allowReviewRequired,
        boolean failOnConflict,
        boolean runCompileGate,
        boolean runRuleRecheck,
        String operator
) {
    public FixApplyStrategy {
        operator = operator == null || operator.isBlank() ? "system" : operator;
    }

    public static FixApplyStrategy safeDefault(String operator) {
        return new FixApplyStrategy(false, true, true, true, operator);
    }
}

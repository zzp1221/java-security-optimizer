package com.project.javasecurityoptimizer.rulepack;

public record RulePackValidationResult(
        boolean valid,
        RulePackErrorCode errorCode,
        String message
) {
    public static RulePackValidationResult ok() {
        return new RulePackValidationResult(true, null, "OK");
    }

    public static RulePackValidationResult fail(RulePackErrorCode errorCode, String message) {
        return new RulePackValidationResult(false, errorCode, message);
    }
}

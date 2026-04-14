package com.project.javasecurityoptimizer.security;

public record FixApplyAuditRequest(
        String taskId,
        String rulePackId,
        String fixId,
        String operator,
        Boolean confirmed
) {
}

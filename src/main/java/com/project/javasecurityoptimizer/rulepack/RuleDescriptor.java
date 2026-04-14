package com.project.javasecurityoptimizer.rulepack;

import java.util.Objects;

public record RuleDescriptor(
        String ruleId,
        String description,
        String severity,
        boolean autoFixSupported
) {
    public RuleDescriptor {
        Objects.requireNonNull(ruleId, "ruleId must not be null");
        Objects.requireNonNull(description, "description must not be null");
        Objects.requireNonNull(severity, "severity must not be null");
    }
}

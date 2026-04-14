package com.project.javasecurityoptimizer.governance;

import java.util.Objects;

public record RuleParameterDefinition(
        String key,
        String displayName,
        String description,
        int minValue,
        int maxValue,
        int defaultValue
) {
    public RuleParameterDefinition {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(displayName, "displayName must not be null");
        Objects.requireNonNull(description, "description must not be null");
        if (minValue > maxValue) {
            throw new IllegalArgumentException("minValue must be <= maxValue");
        }
        if (defaultValue < minValue || defaultValue > maxValue) {
            throw new IllegalArgumentException("defaultValue out of range");
        }
    }
}

package com.project.javasecurityoptimizer.rulepack;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

public record RulePackCompatibility(
        String engineVersionRange,
        Set<ReleaseEnvironment> environments
) {
    public RulePackCompatibility {
        Objects.requireNonNull(engineVersionRange, "engineVersionRange must not be null");
        environments = environments == null || environments.isEmpty()
                ? EnumSet.allOf(ReleaseEnvironment.class)
                : EnumSet.copyOf(environments);
    }

    public static RulePackCompatibility of(String engineVersionRange) {
        return new RulePackCompatibility(engineVersionRange, EnumSet.allOf(ReleaseEnvironment.class));
    }

    public boolean supportsEnvironment(ReleaseEnvironment environment) {
        return environments.contains(environment);
    }
}

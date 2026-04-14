package com.project.javasecurityoptimizer.governance;

import java.util.LinkedHashMap;
import java.util.Map;

public record RuleGovernanceDashboard(
        Map<String, Long> ruleHitCounts,
        Map<String, Long> ruleDurationMillis,
        Map<String, Long> falsePositiveCounts
) {
    public RuleGovernanceDashboard {
        ruleHitCounts = ruleHitCounts == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(ruleHitCounts));
        ruleDurationMillis = ruleDurationMillis == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(ruleDurationMillis));
        falsePositiveCounts = falsePositiveCounts == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(falsePositiveCounts));
    }
}

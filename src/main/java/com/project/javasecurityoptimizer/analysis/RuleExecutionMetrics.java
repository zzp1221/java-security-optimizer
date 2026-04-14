package com.project.javasecurityoptimizer.analysis;

import java.util.LinkedHashMap;
import java.util.Map;

public record RuleExecutionMetrics(
        Map<String, Integer> ruleHitCounts,
        Map<String, Long> ruleDurationMillis
) {
    public RuleExecutionMetrics {
        ruleHitCounts = ruleHitCounts == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(ruleHitCounts));
        ruleDurationMillis = ruleDurationMillis == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(ruleDurationMillis));
    }
}

package com.project.javasecurityoptimizer.governance;

import java.util.LinkedHashMap;
import java.util.Map;

public final class RuleParameterSchema {
    private final Map<String, RuleParameterDefinition> definitions;

    private RuleParameterSchema(Map<String, RuleParameterDefinition> definitions) {
        this.definitions = Map.copyOf(new LinkedHashMap<>(definitions));
    }

    public static RuleParameterSchema defaultSchema() {
        Map<String, RuleParameterDefinition> defs = new LinkedHashMap<>();
        defs.put("complexity.threshold", new RuleParameterDefinition(
                "complexity.threshold",
                "复杂度阈值",
                "方法圈复杂度阈值，超过则触发复杂度告警",
                5,
                50,
                15
        ));
        defs.put("method.length.threshold", new RuleParameterDefinition(
                "method.length.threshold",
                "方法长度阈值",
                "方法行数阈值，超过则触发方法过长告警",
                20,
                500,
                120
        ));
        defs.put("duplication.threshold", new RuleParameterDefinition(
                "duplication.threshold",
                "重复度阈值",
                "重复代码片段阈值（语句数）",
                3,
                50,
                8
        ));
        return new RuleParameterSchema(defs);
    }

    public Map<String, RuleParameterDefinition> definitions() {
        return definitions;
    }

    public Map<String, Integer> defaultValues() {
        Map<String, Integer> values = new LinkedHashMap<>();
        for (RuleParameterDefinition definition : definitions.values()) {
            values.put(definition.key(), definition.defaultValue());
        }
        return values;
    }

    public Map<String, Integer> sanitize(Map<String, Integer> input) {
        Map<String, Integer> sanitized = new LinkedHashMap<>(defaultValues());
        if (input == null || input.isEmpty()) {
            return Map.copyOf(sanitized);
        }
        for (Map.Entry<String, Integer> entry : input.entrySet()) {
            RuleParameterDefinition definition = definitions.get(entry.getKey());
            if (definition == null || entry.getValue() == null) {
                continue;
            }
            int value = Math.max(definition.minValue(), Math.min(definition.maxValue(), entry.getValue()));
            sanitized.put(entry.getKey(), value);
        }
        return Map.copyOf(sanitized);
    }
}

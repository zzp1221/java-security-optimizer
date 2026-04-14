package com.project.javasecurityoptimizer.governance;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record ProjectRulePolicy(
        String workspaceId,
        RuleTemplateType template,
        Set<String> enabledRuleIds,
        Set<String> disabledRuleIds,
        Map<String, Integer> parameters,
        Instant updatedAt
) {
    public ProjectRulePolicy {
        Objects.requireNonNull(workspaceId, "workspaceId must not be null");
        Objects.requireNonNull(template, "template must not be null");
        enabledRuleIds = enabledRuleIds == null ? Set.of() : Set.copyOf(new LinkedHashSet<>(enabledRuleIds));
        disabledRuleIds = disabledRuleIds == null ? Set.of() : Set.copyOf(new LinkedHashSet<>(disabledRuleIds));
        parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
        updatedAt = updatedAt == null ? Instant.now() : updatedAt;
    }
}

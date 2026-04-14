package com.project.javasecurityoptimizer.rulepack;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryProjectRulePackBindingRepository implements ProjectRulePackBindingRepository {
    private final Map<String, ProjectRulePackBinding> bindings = new ConcurrentHashMap<>();

    @Override
    public void bind(ProjectRulePackBinding binding) {
        bindings.put(binding.workspaceId(), binding);
    }

    @Override
    public Optional<ProjectRulePackBinding> findByWorkspaceId(String workspaceId) {
        if (workspaceId == null || workspaceId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(bindings.get(workspaceId));
    }
}

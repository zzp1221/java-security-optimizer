package com.project.javasecurityoptimizer.governance;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryProjectRulePolicyRepository implements ProjectRulePolicyRepository {
    private final Map<String, ProjectRulePolicy> storage = new ConcurrentHashMap<>();

    @Override
    public void save(ProjectRulePolicy policy) {
        Objects.requireNonNull(policy, "policy must not be null");
        storage.put(policy.workspaceId(), policy);
    }

    @Override
    public Optional<ProjectRulePolicy> findByWorkspaceId(String workspaceId) {
        if (workspaceId == null || workspaceId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(storage.get(workspaceId));
    }
}

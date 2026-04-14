package com.project.javasecurityoptimizer.rulepack;

import java.util.Optional;

public interface ProjectRulePackBindingRepository {
    void bind(ProjectRulePackBinding binding);

    Optional<ProjectRulePackBinding> findByWorkspaceId(String workspaceId);
}

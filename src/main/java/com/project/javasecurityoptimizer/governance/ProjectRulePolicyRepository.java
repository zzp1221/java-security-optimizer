package com.project.javasecurityoptimizer.governance;

import java.util.Optional;

public interface ProjectRulePolicyRepository {
    void save(ProjectRulePolicy policy);

    Optional<ProjectRulePolicy> findByWorkspaceId(String workspaceId);
}

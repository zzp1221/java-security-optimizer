package com.project.javasecurityoptimizer.rulepack;

import java.util.List;
import java.util.Set;

public interface RulePackLocalRepository {
    void install(RulePackManifest manifest);

    List<InstalledRulePack> installedPacks();

    Set<String> activeRuleSet();

    void setActiveRuleSet(Set<String> ruleIds);
}

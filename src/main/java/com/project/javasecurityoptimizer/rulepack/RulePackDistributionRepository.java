package com.project.javasecurityoptimizer.rulepack;

import java.util.List;
import java.util.Optional;

public interface RulePackDistributionRepository {
    void publish(RulePackDistributionRecord record);

    Optional<RulePackDistributionRecord> find(String packId, String version);

    Optional<RulePackDistributionRecord> current(String packId);

    void setCurrent(String packId, String version);

    List<RulePackDistributionRecord> history(String packId);
}

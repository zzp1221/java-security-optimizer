package com.project.javasecurityoptimizer.rulepack;

import java.time.Instant;
import java.util.List;

public record InstalledRulePack(
        String packId,
        String version,
        String language,
        String checksum,
        Instant installedAt,
        List<String> ruleIds
) {
    public InstalledRulePack {
        ruleIds = ruleIds == null ? List.of() : List.copyOf(ruleIds);
    }
}

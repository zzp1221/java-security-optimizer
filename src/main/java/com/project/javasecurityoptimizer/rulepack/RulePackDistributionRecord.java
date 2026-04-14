package com.project.javasecurityoptimizer.rulepack;

import java.time.Instant;
import java.util.Objects;

public record RulePackDistributionRecord(
        String packId,
        String version,
        RulePackManifest manifest,
        Instant publishedAt,
        String publishedBy
) {
    public RulePackDistributionRecord {
        Objects.requireNonNull(packId, "packId must not be null");
        Objects.requireNonNull(version, "version must not be null");
        Objects.requireNonNull(manifest, "manifest must not be null");
        publishedAt = publishedAt == null ? Instant.now() : publishedAt;
        publishedBy = publishedBy == null ? "system" : publishedBy;
    }
}

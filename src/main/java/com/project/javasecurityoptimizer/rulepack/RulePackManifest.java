package com.project.javasecurityoptimizer.rulepack;

import java.util.List;
import java.util.Objects;

public record RulePackManifest(
        String packId,
        String version,
        String language,
        String engineVersionRange,
        RulePackCompatibility compatibility,
        String checksum,
        List<RuleDescriptor> rules,
        SignatureSpec signature
) {
    public RulePackManifest {
        Objects.requireNonNull(packId, "packId must not be null");
        Objects.requireNonNull(version, "version must not be null");
        Objects.requireNonNull(language, "language must not be null");
        Objects.requireNonNull(engineVersionRange, "engineVersionRange must not be null");
        compatibility = compatibility == null ? RulePackCompatibility.of(engineVersionRange) : compatibility;
        Objects.requireNonNull(checksum, "checksum must not be null");
        rules = rules == null ? List.of() : List.copyOf(rules);
        Objects.requireNonNull(signature, "signature must not be null");
    }

    public RulePackManifest(
            String packId,
            String version,
            String language,
            String engineVersionRange,
            String checksum,
            List<RuleDescriptor> rules,
            SignatureSpec signature
    ) {
        this(packId, version, language, engineVersionRange, RulePackCompatibility.of(engineVersionRange), checksum, rules, signature);
    }
}

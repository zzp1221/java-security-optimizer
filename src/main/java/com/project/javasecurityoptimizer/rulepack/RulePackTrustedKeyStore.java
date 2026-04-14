package com.project.javasecurityoptimizer.rulepack;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class RulePackTrustedKeyStore {
    private final boolean permissive;
    private final Map<ReleaseEnvironment, Map<String, TrustedKey>> trustedKeys;

    private RulePackTrustedKeyStore(boolean permissive, Map<ReleaseEnvironment, Map<String, TrustedKey>> trustedKeys) {
        this.permissive = permissive;
        this.trustedKeys = trustedKeys;
    }

    public static RulePackTrustedKeyStore permissive() {
        return new RulePackTrustedKeyStore(true, Map.of());
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean isPermissive() {
        return permissive;
    }

    public Optional<TrustedKey> findTrustedKey(ReleaseEnvironment environment, String keyId) {
        Objects.requireNonNull(environment, "environment must not be null");
        if (keyId == null || keyId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(trustedKeys.getOrDefault(environment, Map.of()).get(keyId));
    }

    public record TrustedKey(
            String keyId,
            String algorithm,
            String publicKey
    ) {
        public TrustedKey {
            Objects.requireNonNull(keyId, "keyId must not be null");
            Objects.requireNonNull(algorithm, "algorithm must not be null");
            Objects.requireNonNull(publicKey, "publicKey must not be null");
        }
    }

    public static final class Builder {
        private final Map<ReleaseEnvironment, Map<String, TrustedKey>> trustedKeys = new EnumMap<>(ReleaseEnvironment.class);

        public Builder addTrustedKey(ReleaseEnvironment environment, String keyId, String algorithm, String publicKey) {
            Objects.requireNonNull(environment, "environment must not be null");
            TrustedKey trustedKey = new TrustedKey(keyId, algorithm, publicKey);
            trustedKeys.computeIfAbsent(environment, env -> new HashMap<>()).put(keyId, trustedKey);
            return this;
        }

        public RulePackTrustedKeyStore build() {
            Map<ReleaseEnvironment, Map<String, TrustedKey>> copied = new EnumMap<>(ReleaseEnvironment.class);
            for (Map.Entry<ReleaseEnvironment, Map<String, TrustedKey>> entry : trustedKeys.entrySet()) {
                copied.put(entry.getKey(), Map.copyOf(entry.getValue()));
            }
            return new RulePackTrustedKeyStore(false, Map.copyOf(copied));
        }
    }
}

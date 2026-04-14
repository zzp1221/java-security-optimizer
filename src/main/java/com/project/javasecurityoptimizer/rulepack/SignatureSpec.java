package com.project.javasecurityoptimizer.rulepack;

import java.util.Objects;

public record SignatureSpec(
        String keyId,
        String algorithm,
        String publicKey,
        String value
) {
    public SignatureSpec {
        Objects.requireNonNull(keyId, "keyId must not be null");
        Objects.requireNonNull(algorithm, "algorithm must not be null");
        Objects.requireNonNull(publicKey, "publicKey must not be null");
        Objects.requireNonNull(value, "value must not be null");
    }
}

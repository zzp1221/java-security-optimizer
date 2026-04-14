package com.project.javasecurityoptimizer.rulepack;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

public class RulePackValidator {
    private final RulePackTrustedKeyStore trustedKeyStore;

    public RulePackValidator() {
        this(RulePackTrustedKeyStore.permissive());
    }

    public RulePackValidator(RulePackTrustedKeyStore trustedKeyStore) {
        this.trustedKeyStore = Objects.requireNonNull(trustedKeyStore, "trustedKeyStore must not be null");
    }

    public RulePackValidationResult validate(Path packageFile, RulePackManifest manifest, String currentEngineVersion) {
        return validate(packageFile, manifest, currentEngineVersion, RulePackSecurityContext.permissive());
    }

    public RulePackValidationResult validate(
            Path packageFile,
            RulePackManifest manifest,
            String currentEngineVersion,
            RulePackSecurityContext securityContext
    ) {
        Objects.requireNonNull(packageFile, "packageFile must not be null");
        Objects.requireNonNull(manifest, "manifest must not be null");
        Objects.requireNonNull(currentEngineVersion, "currentEngineVersion must not be null");
        Objects.requireNonNull(securityContext, "securityContext must not be null");

        try {
            String actualChecksum = checksum(packageFile);
            if (!actualChecksum.equalsIgnoreCase(manifest.checksum())) {
                return RulePackValidationResult.fail(RulePackErrorCode.CHECKSUM_MISMATCH, "checksum validation failed");
            }
        }
        catch (Exception e) {
            return RulePackValidationResult.fail(RulePackErrorCode.CHECKSUM_MISMATCH, "checksum validation error: " + e.getMessage());
        }

        try {
            String engineRange = manifest.compatibility().engineVersionRange();
            EngineVersionRange range = EngineVersionRange.parse(engineRange);
            if (!range.contains(currentEngineVersion)) {
                return RulePackValidationResult.fail(
                        RulePackErrorCode.ENGINE_VERSION_INCOMPATIBLE,
                        "compatibility.engineVersionRange validation failed"
                );
            }
            if (!manifest.compatibility().supportsEnvironment(securityContext.environment())) {
                return RulePackValidationResult.fail(
                        RulePackErrorCode.COMPATIBILITY_INCOMPATIBLE,
                        "compatibility.environments does not include environment: " + securityContext.environment()
                );
            }
        } catch (Exception e) {
            return RulePackValidationResult.fail(
                    RulePackErrorCode.ENGINE_VERSION_INCOMPATIBLE,
                    "engineVersionRange validation error: " + e.getMessage()
            );
        }

        try {
            if (!verifySignature(manifest, manifest.signature(), securityContext.environment())) {
                return RulePackValidationResult.fail(
                        RulePackErrorCode.SIGNATURE_VERIFICATION_FAILED,
                        "signature validation failed"
                );
            }
        } catch (Exception e) {
            return RulePackValidationResult.fail(
                    RulePackErrorCode.SIGNATURE_VERIFICATION_FAILED,
                    "signature validation error: " + e.getMessage()
            );
        }

        return RulePackValidationResult.ok();
    }

    public String checksum(Path packageFile) throws Exception {
        byte[] bytes = Files.readAllBytes(packageFile);
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(bytes)).toLowerCase(Locale.ROOT);
    }

    private boolean verifySignature(RulePackManifest manifest, SignatureSpec signatureSpec, ReleaseEnvironment environment) throws Exception {
        RulePackTrustedKeyStore.TrustedKey trustedKey = resolveTrustedKey(signatureSpec, environment);
        String algorithm = trustedKey == null ? signatureSpec.algorithm() : trustedKey.algorithm();
        String payload = canonicalPayload(manifest);
        byte[] signatureBytes = Base64.getDecoder().decode(signatureSpec.value());

        PublicKey publicKey = parsePublicKey(algorithm, trustedKey == null ? signatureSpec.publicKey() : trustedKey.publicKey());
        Signature verifier = Signature.getInstance(algorithm);
        verifier.initVerify(publicKey);
        verifier.update(payload.getBytes(StandardCharsets.UTF_8));
        return verifier.verify(signatureBytes);
    }

    private RulePackTrustedKeyStore.TrustedKey resolveTrustedKey(SignatureSpec signatureSpec, ReleaseEnvironment environment) {
        if (trustedKeyStore.isPermissive()) {
            return null;
        }
        RulePackTrustedKeyStore.TrustedKey trustedKey = trustedKeyStore
                .findTrustedKey(environment, signatureSpec.keyId())
                .orElseThrow(() -> new IllegalArgumentException("untrusted keyId in environment: " + signatureSpec.keyId()));
        if (!trustedKey.algorithm().equalsIgnoreCase(signatureSpec.algorithm())) {
            throw new IllegalArgumentException("algorithm mismatch for trusted keyId: " + signatureSpec.keyId());
        }
        if (!trustedKey.publicKey().equals(signatureSpec.publicKey())) {
            throw new IllegalArgumentException("public key mismatch for trusted keyId: " + signatureSpec.keyId());
        }
        return trustedKey;
    }

    private PublicKey parsePublicKey(String algorithm, String base64Der) throws Exception {
        byte[] keyBytes = Base64.getDecoder().decode(base64Der);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance(keyFactoryAlgorithm(algorithm));
        return keyFactory.generatePublic(spec);
    }

    private String keyFactoryAlgorithm(String signatureAlgorithm) {
        if ("SHA256withRSA".equalsIgnoreCase(signatureAlgorithm)) {
            return "RSA";
        }
        if ("SHA256withECDSA".equalsIgnoreCase(signatureAlgorithm)) {
            return "EC";
        }
        throw new IllegalArgumentException("unsupported signature algorithm: " + signatureAlgorithm);
    }

    String canonicalPayload(RulePackManifest manifest) {
        String ruleIds = manifest.rules().stream()
                .map(RuleDescriptor::ruleId)
                .sorted()
                .collect(Collectors.joining(","));
        String fields = String.join("|",
                manifest.packId(),
                manifest.version(),
                manifest.language(),
                manifest.compatibility().engineVersionRange(),
                manifest.compatibility().environments().stream().map(Enum::name).sorted().collect(Collectors.joining(",")),
                manifest.checksum(),
                ruleIds
        );
        return fields;
    }
}

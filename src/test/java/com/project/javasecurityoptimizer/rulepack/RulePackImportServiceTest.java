package com.project.javasecurityoptimizer.rulepack;

import com.project.javasecurityoptimizer.security.SecurityAuditAction;
import com.project.javasecurityoptimizer.security.SecurityAuditService;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Signature;
import java.util.Base64;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RulePackImportServiceTest {
    @Test
    void shouldValidateAndImportPack() throws Exception {
        KeyPair keyPair = rsaKeyPair();
        Path packageFile = Files.createTempFile("rule-pack", ".bin");
        Files.writeString(packageFile, "payload-v1");

        RulePackValidator validator = new RulePackValidator();
        String checksum = validator.checksum(packageFile);
        RulePackManifest unsignedManifest = manifest(checksum, "[1.0.0,2.0.0)", keyPair, "dev-key-1", "");
        String signature = sign(validator.canonicalPayload(unsignedManifest), keyPair);
        RulePackManifest manifest = manifest(checksum, "[1.0.0,2.0.0)", keyPair, "dev-key-1", signature);

        InMemoryRulePackLocalRepository repository = new InMemoryRulePackLocalRepository();
        RulePackImportService importService = new RulePackImportService(validator, repository, "1.1.0");

        RulePackValidationResult result = importService.importPack(packageFile, manifest);

        assertTrue(result.valid());
        assertNull(result.errorCode());
        assertEquals(1, repository.installedPacks().size());
        assertTrue(repository.activeRuleSet().contains("JAVA.STRING.EQUALITY"));
    }

    @Test
    void shouldRejectImportWhenEngineVersionOutOfRange() throws Exception {
        KeyPair keyPair = rsaKeyPair();
        Path packageFile = Files.createTempFile("rule-pack-out", ".bin");
        Files.writeString(packageFile, "payload-v2");

        RulePackValidator validator = new RulePackValidator();
        String checksum = validator.checksum(packageFile);
        RulePackManifest unsignedManifest = manifest(checksum, "[2.0.0,3.0.0)", keyPair, "dev-key-1", "");
        String signature = sign(validator.canonicalPayload(unsignedManifest), keyPair);
        RulePackManifest manifest = manifest(checksum, "[2.0.0,3.0.0)", keyPair, "dev-key-1", signature);

        InMemoryRulePackLocalRepository repository = new InMemoryRulePackLocalRepository();
        RulePackImportService importService = new RulePackImportService(validator, repository, "1.5.0");

        RulePackValidationResult result = importService.importPack(packageFile, manifest);

        assertFalse(result.valid());
        assertEquals(RulePackErrorCode.ENGINE_VERSION_INCOMPATIBLE, result.errorCode());
        assertEquals(0, repository.installedPacks().size());
    }

    @Test
    void shouldRejectImportWhenChecksumMismatch() throws Exception {
        KeyPair keyPair = rsaKeyPair();
        Path packageFile = Files.createTempFile("rule-pack-checksum", ".bin");
        Files.writeString(packageFile, "payload-v3");

        RulePackValidator validator = new RulePackValidator();
        String checksum = checksum("different-payload".getBytes(StandardCharsets.UTF_8));
        RulePackManifest unsignedManifest = manifest(checksum, "1.2.0", keyPair, "dev-key-1", "");
        String signature = sign(validator.canonicalPayload(unsignedManifest), keyPair);
        RulePackManifest manifest = manifest(checksum, "1.2.0", keyPair, "dev-key-1", signature);

        InMemoryRulePackLocalRepository repository = new InMemoryRulePackLocalRepository();
        RulePackImportService importService = new RulePackImportService(validator, repository, "1.2.0");

        RulePackValidationResult result = importService.importPack(packageFile, manifest);

        assertFalse(result.valid());
        assertEquals(RulePackErrorCode.CHECKSUM_MISMATCH, result.errorCode());
        assertEquals(0, repository.installedPacks().size());
    }

    @Test
    void shouldRejectImportWhenPackagePathOutOfAuthorizedDirectories() throws Exception {
        KeyPair keyPair = rsaKeyPair();
        Path packageFile = Files.createTempFile("rule-pack-denied", ".bin");
        Files.writeString(packageFile, "payload-v4");

        RulePackValidator validator = new RulePackValidator();
        String checksum = validator.checksum(packageFile);
        RulePackManifest unsignedManifest = manifest(checksum, "1.2.0", keyPair, "dev-key-1", "");
        String signature = sign(validator.canonicalPayload(unsignedManifest), keyPair);
        RulePackManifest manifest = manifest(checksum, "1.2.0", keyPair, "dev-key-1", signature);

        InMemoryRulePackLocalRepository repository = new InMemoryRulePackLocalRepository();
        RulePackImportService importService = new RulePackImportService(validator, repository, "1.2.0");
        Path deniedRoot = packageFile.getParent().resolve("not-allowed");
        RulePackSecurityContext context = new RulePackSecurityContext(ReleaseEnvironment.DEV, List.of(deniedRoot));

        RulePackValidationResult result = importService.importPack(packageFile, manifest, context);

        assertFalse(result.valid());
        assertEquals(RulePackErrorCode.PERMISSION_OUT_OF_BOUNDS, result.errorCode());
        assertEquals(0, repository.installedPacks().size());
    }

    @Test
    void shouldRejectProdImportWhenSignedByDevKey() throws Exception {
        KeyPair devKeyPair = rsaKeyPair();
        KeyPair prodKeyPair = rsaKeyPair();
        String devPublicKey = Base64.getEncoder().encodeToString(devKeyPair.getPublic().getEncoded());
        String prodPublicKey = Base64.getEncoder().encodeToString(prodKeyPair.getPublic().getEncoded());
        RulePackTrustedKeyStore keyStore = RulePackTrustedKeyStore.builder()
                .addTrustedKey(ReleaseEnvironment.DEV, "dev-key-1", "SHA256withRSA", devPublicKey)
                .addTrustedKey(ReleaseEnvironment.PROD, "prod-key-1", "SHA256withRSA", prodPublicKey)
                .build();

        Path packageFile = Files.createTempFile("rule-pack-prod", ".bin");
        Files.writeString(packageFile, "payload-v5");
        RulePackValidator validator = new RulePackValidator(keyStore);

        String checksum = validator.checksum(packageFile);
        RulePackManifest unsignedManifest = manifest(checksum, "1.2.0", devKeyPair, "dev-key-1", "");
        String signature = sign(validator.canonicalPayload(unsignedManifest), devKeyPair);
        RulePackManifest manifest = manifest(checksum, "1.2.0", devKeyPair, "dev-key-1", signature);

        InMemoryRulePackLocalRepository repository = new InMemoryRulePackLocalRepository();
        RulePackImportService importService = new RulePackImportService(validator, repository, "1.2.0");
        RulePackSecurityContext context = new RulePackSecurityContext(
                ReleaseEnvironment.PROD,
                List.of(packageFile.getParent())
        );

        RulePackValidationResult result = importService.importPack(packageFile, manifest, context);

        assertFalse(result.valid());
        assertEquals(RulePackErrorCode.SIGNATURE_VERIFICATION_FAILED, result.errorCode());
        assertEquals(0, repository.installedPacks().size());
    }

    @Test
    void shouldRejectImportWhenCompatibilityEnvironmentNotAllowed() throws Exception {
        KeyPair keyPair = rsaKeyPair();
        Path packageFile = Files.createTempFile("rule-pack-compat-env", ".bin");
        Files.writeString(packageFile, "payload-v6");

        RulePackValidator validator = new RulePackValidator();
        String checksum = validator.checksum(packageFile);
        RulePackManifest unsignedManifest = manifest(checksum, "[1.0.0,2.0.0)", keyPair, "dev-key-1", "");
        RulePackManifest unsignedWithCompatibility = new RulePackManifest(
                unsignedManifest.packId(),
                unsignedManifest.version(),
                unsignedManifest.language(),
                unsignedManifest.engineVersionRange(),
                new RulePackCompatibility(unsignedManifest.engineVersionRange(), EnumSet.of(ReleaseEnvironment.DEV)),
                unsignedManifest.checksum(),
                unsignedManifest.rules(),
                unsignedManifest.signature()
        );
        String signature = sign(validator.canonicalPayload(unsignedWithCompatibility), keyPair);
        RulePackManifest manifest = new RulePackManifest(
                unsignedWithCompatibility.packId(),
                unsignedWithCompatibility.version(),
                unsignedWithCompatibility.language(),
                unsignedWithCompatibility.engineVersionRange(),
                unsignedWithCompatibility.compatibility(),
                unsignedWithCompatibility.checksum(),
                unsignedWithCompatibility.rules(),
                new SignatureSpec("dev-key-1", "SHA256withRSA", unsignedWithCompatibility.signature().publicKey(), signature)
        );

        InMemoryRulePackLocalRepository repository = new InMemoryRulePackLocalRepository();
        RulePackImportService importService = new RulePackImportService(validator, repository, "1.2.0");
        RulePackSecurityContext context = new RulePackSecurityContext(
                ReleaseEnvironment.PROD,
                List.of(packageFile.getParent())
        );

        RulePackValidationResult result = importService.importPack(packageFile, manifest, context);

        assertFalse(result.valid());
        assertEquals(RulePackErrorCode.COMPATIBILITY_INCOMPATIBLE, result.errorCode());
        assertEquals(0, repository.installedPacks().size());
    }

    @Test
    void shouldRecordAuditEventWhenImportRejectedByPermission() throws Exception {
        KeyPair keyPair = rsaKeyPair();
        Path packageFile = Files.createTempFile("rule-pack-audit", ".bin");
        Files.writeString(packageFile, "payload-v7");

        RulePackValidator validator = new RulePackValidator();
        String checksum = validator.checksum(packageFile);
        RulePackManifest unsignedManifest = manifest(checksum, "[1.0.0,2.0.0)", keyPair, "dev-key-1", "");
        String signature = sign(validator.canonicalPayload(unsignedManifest), keyPair);
        RulePackManifest manifest = manifest(checksum, "[1.0.0,2.0.0)", keyPair, "dev-key-1", signature);

        InMemoryRulePackLocalRepository repository = new InMemoryRulePackLocalRepository();
        SecurityAuditService auditService = new SecurityAuditService();
        RulePackImportService importService = new RulePackImportService(validator, repository, "1.1.0", auditService);
        RulePackSecurityContext context = new RulePackSecurityContext(
                ReleaseEnvironment.DEV,
                List.of(packageFile.getParent().resolve("not-allowed"))
        );

        RulePackValidationResult result = importService.importPack(packageFile, manifest, context);

        assertFalse(result.valid());
        assertEquals(RulePackErrorCode.PERMISSION_OUT_OF_BOUNDS, result.errorCode());
        assertEquals(1, auditService.recentEvents(10).size());
        assertEquals(SecurityAuditAction.RULE_PACK_IMPORT.name(), auditService.recentEvents(10).get(0).userAction());
        assertEquals(manifest.packId(), auditService.recentEvents(10).get(0).rulePackId());
    }

    private RulePackManifest manifest(
            String checksum,
            String engineVersionRange,
            KeyPair keyPair,
            String keyId,
            String signature
    ) {
        String publicKey = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
        return new RulePackManifest(
                "pack.java.security.core",
                "1.0.0",
                "java",
                engineVersionRange,
                checksum,
                List.of(
                        new RuleDescriptor("JAVA.STRING.EQUALITY", "Use equals for string compare", "MEDIUM", true),
                        new RuleDescriptor("JAVA.RESOURCE.NOT_CLOSED", "Close streams with try-with-resources", "HIGH", false)
                ),
                new SignatureSpec(keyId, "SHA256withRSA", publicKey, signature)
        );
    }

    private KeyPair rsaKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private String sign(String payload, KeyPair keyPair) throws Exception {
        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(keyPair.getPrivate());
        signer.update(payload.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(signer.sign());
    }

    private String checksum(byte[] content) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(content)).toLowerCase(Locale.ROOT);
    }
}

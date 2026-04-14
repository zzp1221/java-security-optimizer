package com.project.javasecurityoptimizer.rulepack;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Base64;
import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RulePackTeamSharingServiceTest {
    @Test
    void shouldPublishBindImportRollbackAndAudit() throws Exception {
        KeyPair keyPair = rsaKeyPair();
        RulePackValidator validator = new RulePackValidator();
        InMemoryRulePackLocalRepository localRepository = new InMemoryRulePackLocalRepository();
        RulePackImportService importService = new RulePackImportService(validator, localRepository, "1.2.0");
        InMemoryRulePackDistributionRepository distributionRepository = new InMemoryRulePackDistributionRepository();
        InMemoryProjectRulePackBindingRepository bindingRepository = new InMemoryProjectRulePackBindingRepository();
        InMemoryRulePackAuditRepository auditRepository = new InMemoryRulePackAuditRepository();
        RulePackTeamSharingService service = new RulePackTeamSharingService(
                importService,
                distributionRepository,
                bindingRepository,
                auditRepository
        );

        Path packageV1 = Files.createTempFile("team-pack-v1", ".bin");
        Files.writeString(packageV1, "payload-team-v1");
        RulePackManifest manifestV1 = signedManifest(
                validator,
                keyPair,
                packageV1,
                "pack.team.shared",
                "1.0.0",
                EnumSet.of(ReleaseEnvironment.PROD)
        );
        service.publish(manifestV1, "alice");

        Path packageV2 = Files.createTempFile("team-pack-v2", ".bin");
        Files.writeString(packageV2, "payload-team-v2");
        RulePackManifest manifestV2 = signedManifest(
                validator,
                keyPair,
                packageV2,
                "pack.team.shared",
                "1.1.0",
                EnumSet.of(ReleaseEnvironment.PROD)
        );
        service.publish(manifestV2, "alice");

        assertEquals("1.1.0", service.currentDistributedVersion("pack.team.shared").orElseThrow().version());

        RulePackValidationResult bindResult = service.bindWorkspace(
                "workspace-x",
                "pack.team.shared",
                "1.0.0",
                ReleaseEnvironment.PROD,
                "bob"
        );
        assertTrue(bindResult.valid());

        RulePackValidationResult wrongEnvImport = service.importForWorkspace(
                "workspace-x",
                packageV1,
                new RulePackSecurityContext(ReleaseEnvironment.DEV, List.of(packageV1.getParent())),
                "bob"
        );
        assertFalse(wrongEnvImport.valid());
        assertEquals(RulePackErrorCode.ENVIRONMENT_LOCK_MISMATCH, wrongEnvImport.errorCode());

        RulePackValidationResult importResult = service.importForWorkspace(
                "workspace-x",
                packageV1,
                new RulePackSecurityContext(ReleaseEnvironment.PROD, List.of(packageV1.getParent())),
                "bob"
        );
        assertTrue(importResult.valid());
        assertEquals(1, localRepository.installedPacks().size());
        assertEquals("1.0.0", localRepository.installedPacks().getFirst().version());

        RulePackValidationResult rollbackResult = service.rollback("pack.team.shared", "1.0.0", "ops");
        assertTrue(rollbackResult.valid());
        assertEquals("1.0.0", service.currentDistributedVersion("pack.team.shared").orElseThrow().version());

        long publishCount = service.auditEvents().stream().filter(event -> event.action() == RulePackAuditAction.PUBLISH).count();
        long bindCount = service.auditEvents().stream().filter(event -> event.action() == RulePackAuditAction.BIND_WORKSPACE).count();
        long rollbackCount = service.auditEvents().stream().filter(event -> event.action() == RulePackAuditAction.ROLLBACK).count();
        long importRejectedCount = service.auditEvents().stream().filter(event -> event.action() == RulePackAuditAction.IMPORT_REJECTED).count();
        long importAcceptedCount = service.auditEvents().stream().filter(event -> event.action() == RulePackAuditAction.IMPORT_ACCEPTED).count();
        assertEquals(2, publishCount);
        assertEquals(1, bindCount);
        assertEquals(1, rollbackCount);
        assertEquals(1, importRejectedCount);
        assertEquals(1, importAcceptedCount);
    }

    private RulePackManifest signedManifest(
            RulePackValidator validator,
            KeyPair keyPair,
            Path packageFile,
            String packId,
            String version,
            EnumSet<ReleaseEnvironment> environments
    ) throws Exception {
        String checksum = validator.checksum(packageFile);
        String publicKey = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
        RulePackManifest unsigned = new RulePackManifest(
                packId,
                version,
                "java",
                "[1.0.0,2.0.0)",
                new RulePackCompatibility("[1.0.0,2.0.0)", environments),
                checksum,
                List.of(new RuleDescriptor("JAVA.STRING.EQUALITY", "desc", "MEDIUM", true)),
                new SignatureSpec("team-key-1", "SHA256withRSA", publicKey, "")
        );
        String signature = sign(validator.canonicalPayload(unsigned), keyPair);
        return new RulePackManifest(
                unsigned.packId(),
                unsigned.version(),
                unsigned.language(),
                unsigned.engineVersionRange(),
                unsigned.compatibility(),
                unsigned.checksum(),
                unsigned.rules(),
                new SignatureSpec("team-key-1", "SHA256withRSA", publicKey, signature)
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
}

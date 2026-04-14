package com.project.javasecurityoptimizer.rulepack;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.javasecurityoptimizer.security.SecurityAuditService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RulePackControllerTest {

    @Test
    void shouldImportRulePackSuccessfully() throws Exception {
        InMemoryRulePackLocalRepository repository = new InMemoryRulePackLocalRepository();
        RulePackValidator validator = new RulePackValidator();
        RulePackImportService importService = new RulePackImportService(
                validator,
                repository,
                "1.1.0",
                new SecurityAuditService()
        );
        RulePackController controller = new RulePackController(importService, repository, new ObjectMapper());

        KeyPair keyPair = rsaKeyPair();
        Path packageFile = Files.createTempFile("controller-rule-pack", ".bin");
        Files.writeString(packageFile, "payload-controller");
        String checksum = validator.checksum(packageFile);
        RulePackManifest unsigned = manifest(checksum, keyPair, "");
        String signature = sign(validator.canonicalPayload(unsigned), keyPair);
        RulePackManifest signedManifest = manifest(checksum, keyPair, signature);

        MockMultipartFile packageMultipart = new MockMultipartFile(
                "packageFile",
                "custom-pack.bin",
                "application/octet-stream",
                Files.readAllBytes(packageFile)
        );
        MockMultipartFile manifestMultipart = new MockMultipartFile(
                "manifestFile",
                "manifest.json",
                "application/json",
                new ObjectMapper().writeValueAsBytes(signedManifest)
        );

        ResponseEntity<RulePackController.RulePackImportResponse> response = controller.importRulePack(
                packageMultipart,
                manifestMultipart,
                null,
                "DEV"
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().success());
        assertEquals(1, controller.installedRulePacks().size());
    }

    @Test
    void shouldRejectImportWhenManifestNotProvided() throws Exception {
        InMemoryRulePackLocalRepository repository = new InMemoryRulePackLocalRepository();
        RulePackImportService importService = new RulePackImportService(
                new RulePackValidator(),
                repository,
                "1.1.0",
                new SecurityAuditService()
        );
        RulePackController controller = new RulePackController(importService, repository, new ObjectMapper());

        MockMultipartFile packageMultipart = new MockMultipartFile(
                "packageFile",
                "custom-pack.bin",
                "application/octet-stream",
                "payload".getBytes(StandardCharsets.UTF_8)
        );

        ResponseStatusException exception = org.junit.jupiter.api.Assertions.assertThrows(
                ResponseStatusException.class,
                () -> controller.importRulePack(packageMultipart, null, null, "DEV")
        );
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void shouldReturnErrorPayloadWhenSignatureInvalid() throws Exception {
        InMemoryRulePackLocalRepository repository = new InMemoryRulePackLocalRepository();
        RulePackValidator validator = new RulePackValidator();
        RulePackImportService importService = new RulePackImportService(
                validator,
                repository,
                "1.1.0",
                new SecurityAuditService()
        );
        RulePackController controller = new RulePackController(importService, repository, new ObjectMapper());

        KeyPair keyPair = rsaKeyPair();
        Path packageFile = Files.createTempFile("controller-rule-pack-bad", ".bin");
        Files.writeString(packageFile, "payload-controller-bad");
        String checksum = validator.checksum(packageFile);
        RulePackManifest invalidManifest = manifest(checksum, keyPair, "invalid-signature");

        MockMultipartFile packageMultipart = new MockMultipartFile(
                "packageFile",
                "custom-pack.bin",
                "application/octet-stream",
                Files.readAllBytes(packageFile)
        );
        MockMultipartFile manifestMultipart = new MockMultipartFile(
                "manifestFile",
                "manifest.json",
                "application/json",
                new ObjectMapper().writeValueAsBytes(invalidManifest)
        );

        ResponseEntity<RulePackController.RulePackImportResponse> response = controller.importRulePack(
                packageMultipart,
                manifestMultipart,
                null,
                "DEV"
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().success());
        assertEquals(RulePackErrorCode.SIGNATURE_VERIFICATION_FAILED.name(), response.getBody().errorCode());
    }

    private RulePackManifest manifest(String checksum, KeyPair keyPair, String signature) {
        String publicKey = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
        return new RulePackManifest(
                "pack.java.custom",
                "1.0.0",
                "java",
                "[1.0.0,2.0.0)",
                checksum,
                List.of(
                        new RuleDescriptor("JAVA.STRING.EQUALITY", "Use equals for string compare", "MEDIUM", true)
                ),
                new SignatureSpec("dev-key-1", "SHA256withRSA", publicKey, signature)
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

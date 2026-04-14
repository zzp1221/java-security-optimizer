package com.project.javasecurityoptimizer.plugin;

import com.project.javasecurityoptimizer.analysis.AnalyzeTaskRequest;
import com.project.javasecurityoptimizer.rulepack.RuleDescriptor;
import com.project.javasecurityoptimizer.rulepack.RulePackErrorCode;
import com.project.javasecurityoptimizer.rulepack.RulePackManifest;
import com.project.javasecurityoptimizer.rulepack.RulePackSecurityContext;
import com.project.javasecurityoptimizer.rulepack.RulePackValidationResult;
import com.project.javasecurityoptimizer.rulepack.SignatureSpec;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class JavaLanguagePluginTest {
    @Test
    void shouldAnalyzeJavaProjectWithBuiltinPlugin() throws IOException {
        Path projectDir = Files.createTempDirectory("java-plugin-test");
        Files.writeString(projectDir.resolve("Demo.java"), """
                class Demo {
                    void run() {
                        String a = "1";
                        String b = "2";
                        boolean same = a == b;
                    }
                }
                """);

        JavaLanguagePlugin plugin = new JavaLanguagePlugin();
        var result = plugin.analyze(AnalyzeTaskRequest.full(projectDir));

        assertFalse(result.issues().isEmpty());
        assertFalse(result.events().isEmpty());
    }

    @Test
    void shouldValidateRulePackThroughPlugin() throws IOException {
        Path packageFile = Files.createTempFile("rule-pack", ".zip");
        Files.write(packageFile, "demo".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        JavaLanguagePlugin plugin = new JavaLanguagePlugin();
        RulePackManifest manifest = new RulePackManifest(
                "pack.demo",
                "1.0.0",
                "java",
                "[1.0.0,2.0.0)",
                "bad-checksum",
                List.of(new RuleDescriptor("JAVA.STRING.EQUALITY", "desc", "MEDIUM", true)),
                new SignatureSpec(
                        "demo-key",
                        "SHA256withRSA",
                        Base64.getEncoder().encodeToString("invalid".getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                        Base64.getEncoder().encodeToString("invalid-signature".getBytes(java.nio.charset.StandardCharsets.UTF_8))
                )
        );

        RulePackValidationResult result = plugin.validateRulePack(packageFile, manifest, RulePackSecurityContext.permissive());
        assertFalse(result.valid());
        assertEquals(RulePackErrorCode.CHECKSUM_MISMATCH, result.errorCode());
    }
}

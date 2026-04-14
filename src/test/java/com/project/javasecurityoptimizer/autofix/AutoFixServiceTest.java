package com.project.javasecurityoptimizer.autofix;

import com.project.javasecurityoptimizer.analysis.AnalysisIssue;
import com.project.javasecurityoptimizer.analysis.FixCandidate;
import com.project.javasecurityoptimizer.analysis.FixSafetyLevel;
import com.project.javasecurityoptimizer.analysis.IssueSeverity;
import com.project.javasecurityoptimizer.analysis.JavaAnalysisEngine;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutoFixServiceTest {
    @Test
    void shouldApplyAndRollbackSingleFix() throws IOException {
        Path projectDir = Files.createTempDirectory("autofix-apply-rollback");
        Path file = projectDir.resolve("Sample.java");
        Files.writeString(file, """
                class Sample {
                    void run() {
                        String a = "1";
                    }
                }
                """);

        PatchEdit edit = new PatchEdit(
                file,
                3,
                3,
                "        String a = \"2\";",
                "        String a = \"1\";",
                "TEST.RULE",
                FixSafetyLevel.SAFE
        );
        FixPlanItem item = new FixPlanItem(
                new AnalysisIssue("TEST.RULE", "msg", file, 3, IssueSeverity.LOW, List.of()),
                new FixCandidate("title", "suggestion", FixSafetyLevel.SAFE),
                List.of(edit)
        );

        AutoFixService service = new AutoFixService(new PatchEngine(), new JavaAnalysisEngine(List.of()));
        AutoFixResult result = service.applySingle(projectDir, item, new FixApplyStrategy(true, true, false, false, "tester"));
        assertTrue(result.applied());
        assertTrue(Files.readString(file).contains("\"2\""));
        assertTrue(service.rollback(result.rollbackId(), "tester"));
        assertTrue(Files.readString(file).contains("\"1\""));
    }

    @Test
    void shouldFailQualityGateAndAutoRollback() throws IOException {
        Path projectDir = Files.createTempDirectory("autofix-quality-gate");
        Path file = projectDir.resolve("Broken.java");
        Files.writeString(file, """
                class Broken {
                    void run() {
                        int a = 1;
                    }
                }
                """);

        PatchEdit edit = new PatchEdit(
                file,
                3,
                3,
                "        int a = ;",
                "        int a = 1;",
                "TEST.RULE",
                FixSafetyLevel.SAFE
        );
        FixPlanItem item = new FixPlanItem(
                new AnalysisIssue("TEST.RULE", "msg", file, 3, IssueSeverity.LOW, List.of()),
                new FixCandidate("title", "suggestion", FixSafetyLevel.SAFE),
                List.of(edit)
        );

        AutoFixService service = new AutoFixService(new PatchEngine(), new JavaAnalysisEngine(List.of()));
        AutoFixResult result = service.applySingle(projectDir, item, new FixApplyStrategy(true, true, true, false, "tester"));
        assertFalse(result.applied());
        assertTrue(result.qualityGateMessage().contains("quality gate failed"));
        assertTrue(Files.readString(file).contains("int a = 1;"));
    }
}

package com.project.javasecurityoptimizer.analysis;

import com.project.javasecurityoptimizer.rulepack.InMemoryRulePackLocalRepository;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaAnalysisEngineTest {

    @Test
    void shouldRunMainPipelineAndHitRules() throws IOException {
        Path projectDir = Files.createTempDirectory("analysis-engine-test");
        Path javaFile = projectDir.resolve("Sample.java");
        Files.writeString(javaFile, """
                import java.io.FileInputStream;

                class Sample {
                    void demo() throws Exception {
                        String a = "x";
                        String b = "y";
                        if (a == b) {
                            System.out.println("bad");
                        }
                        String result = "";
                        for (int i = 0; i < 3; i++) {
                            result += i;
                            Thread.sleep(1);
                        }
                        FileInputStream in = new FileInputStream("a.txt");
                        try {
                            throw new RuntimeException();
                        } catch (Exception e) {
                        }
                    }
                }
                """);

        JavaAnalysisEngine engine = new JavaAnalysisEngine();
        AnalyzeTaskRequest request = new AnalyzeTaskRequest(
                projectDir,
                Set.of(),
                AnalyzeMode.FULL,
                Set.of(),
                1024 * 1024,
                2,
                Duration.ofSeconds(2)
        );

        AnalyzeTaskResult result = engine.analyze(request);
        Set<String> hitRules = result.issues().stream().map(AnalysisIssue::ruleId).collect(java.util.stream.Collectors.toSet());

        assertFalse(result.events().isEmpty());
        assertEquals(1, result.stats().indexedFiles());
        assertEquals(1, result.stats().parsedFiles());
        assertTrue(hitRules.contains("JAVA.STRING.EQUALITY"));
        assertTrue(hitRules.contains("JAVA.PERF.LOOP_STRING_CONCAT"));
        assertTrue(hitRules.contains("JAVA.RESOURCE.NOT_CLOSED"));
        assertTrue(hitRules.contains("JAVA.EXCEPTION.EMPTY_CATCH"));
        assertTrue(hitRules.contains("JAVA.EXCEPTION.GENERIC_CATCH"));
        assertTrue(hitRules.contains("JAVA.CONCURRENCY.SLEEP_IN_LOOP"));
    }

    @Test
    void shouldGenerateFixCandidatesForTwoRules() throws IOException {
        Path projectDir = Files.createTempDirectory("analysis-fix-test");
        Path javaFile = projectDir.resolve("FixSample.java");
        Files.writeString(javaFile, """
                class FixSample {
                    void run() {
                        String a = "1";
                        String b = "2";
                        boolean same = a == b;
                        String out = "";
                        for (int i = 0; i < 2; i++) {
                            out += i;
                        }
                    }
                }
                """);

        JavaAnalysisEngine engine = new JavaAnalysisEngine();
        AnalyzeTaskResult result = engine.analyze(AnalyzeTaskRequest.full(projectDir));

        boolean hasStringFix = result.issues().stream()
                .filter(issue -> issue.ruleId().equals("JAVA.STRING.EQUALITY"))
                .anyMatch(issue -> !issue.fixCandidates().isEmpty()
                        && issue.fixCandidates().getFirst().title().contains("Objects.equals"));
        boolean hasLoopFix = result.issues().stream()
                .filter(issue -> issue.ruleId().equals("JAVA.PERF.LOOP_STRING_CONCAT"))
                .anyMatch(issue -> !issue.fixCandidates().isEmpty()
                        && issue.fixCandidates().getFirst().suggestion().contains("StringBuilder"));

        assertTrue(hasStringFix);
        assertTrue(hasLoopFix);
    }

    @Test
    void shouldUseRepositoryActiveRuleSetWhenRequestRuleSetIsEmpty() throws IOException {
        Path projectDir = Files.createTempDirectory("analysis-active-rule-test");
        Path javaFile = projectDir.resolve("ActiveRuleSample.java");
        Files.writeString(javaFile, """
                import java.io.FileInputStream;

                class ActiveRuleSample {
                    void run() throws Exception {
                        String a = "1";
                        String b = "2";
                        if (a == b) {
                            System.out.println("bad");
                        }
                        FileInputStream in = new FileInputStream("x.txt");
                    }
                }
                """);

        InMemoryRulePackLocalRepository repository = new InMemoryRulePackLocalRepository();
        repository.setActiveRuleSet(Set.of("JAVA.STRING.EQUALITY"));
        JavaAnalysisEngine engine = new JavaAnalysisEngine(
                java.util.List.of(
                        new com.project.javasecurityoptimizer.analysis.rules.StringEqualityRule(),
                        new com.project.javasecurityoptimizer.analysis.rules.ResourceNotClosedRule()
                ),
                repository
        );

        AnalyzeTaskResult result = engine.analyze(AnalyzeTaskRequest.full(projectDir));
        Set<String> hitRules = result.issues().stream().map(AnalysisIssue::ruleId).collect(java.util.stream.Collectors.toSet());

        assertTrue(hitRules.contains("JAVA.STRING.EQUALITY"));
        assertFalse(hitRules.contains("JAVA.RESOURCE.NOT_CLOSED"));
    }
}

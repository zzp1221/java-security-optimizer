package com.project.javasecurityoptimizer.analysis;

import com.project.javasecurityoptimizer.rulepack.InMemoryRulePackLocalRepository;
import com.github.javaparser.ast.CompilationUnit;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

    @Test
    void shouldAnalyzeChangedAndImpactedFilesInIncrementalMode() throws IOException {
        Path projectDir = Files.createTempDirectory("analysis-incremental-test");
        Path changedFile = projectDir.resolve("Changed.java");
        Path impactedFile = projectDir.resolve("Impacted.java");
        Path untouchedFile = projectDir.resolve("Untouched.java");
        Files.writeString(changedFile, """
                class Changed {
                    void run() {
                        String a = "1";
                        String b = "2";
                        if (a == b) {
                            System.out.println("bad");
                        }
                    }
                }
                """);
        Files.writeString(impactedFile, """
                class Impacted {
                    void run() {
                        String x = "x";
                        String y = "y";
                        if (x == y) {
                            System.out.println("bad");
                        }
                    }
                }
                """);
        Files.writeString(untouchedFile, """
                class Untouched {
                    void run() {
                        System.out.println("ok");
                    }
                }
                """);

        JavaAnalysisEngine engine = new JavaAnalysisEngine();
        AnalyzeTaskRequest request = new AnalyzeTaskRequest(
                projectDir,
                Set.of("JAVA.STRING.EQUALITY"),
                AnalyzeMode.INCREMENTAL,
                Set.of(changedFile),
                Set.of(impactedFile),
                1024 * 1024,
                512 * 1024,
                2,
                Duration.ofSeconds(2),
                1,
                2,
                Duration.ofSeconds(1),
                Set.of("JAVA.STRING.EQUALITY")
        );

        AnalyzeTaskResult result = engine.analyze(request);
        assertEquals(2, result.stats().indexedFiles());
        assertEquals(2, result.stats().parsedFiles());
        assertTrue(result.issues().stream().allMatch(issue -> issue.ruleId().equals("JAVA.STRING.EQUALITY")));
    }


    @Test
    void shouldExposeRuleExecutionMetrics() throws IOException {
        Path projectDir = Files.createTempDirectory("analysis-rule-metrics-test");
        Path javaFile = projectDir.resolve("MetricsSample.java");
        Files.writeString(javaFile, """
                class MetricsSample {
                    void run() {
                        String a = "1";
                        String b = "2";
                        boolean same = a == b;
                    }
                }
                """);

        JavaAnalysisEngine engine = new JavaAnalysisEngine();
        AnalyzeTaskResult result = engine.analyzeWithMetrics(AnalyzeTaskRequest.full(projectDir));

        assertTrue(result.ruleExecutionMetrics().ruleHitCounts().containsKey("JAVA.STRING.EQUALITY"));
        assertTrue(result.ruleExecutionMetrics().ruleDurationMillis().containsKey("JAVA.STRING.EQUALITY"));
        assertTrue(result.ruleExecutionMetrics().ruleDurationMillis().get("JAVA.STRING.EQUALITY") >= 0L);
    }

    @Test
    void shouldProvideTwentyRulesInDefaultEngine() {
        JavaAnalysisEngine engine = new JavaAnalysisEngine();
        assertEquals(20, engine.availableRuleIds().size());
    }

    @Test
    void shouldIncludeImpactedFilesInIncrementalMode() throws IOException {
        Path projectDir = Files.createTempDirectory("analysis-incremental-impacted-test");
        Path changed = projectDir.resolve("Changed.java");
        Path impacted = projectDir.resolve("Impacted.java");
        Files.writeString(changed, """
                class Changed {
                    void run() {}
                }
                """);
        Files.writeString(impacted, """
                class Impacted {
                    void run() {
                        String a = "1";
                        String b = "2";
                        boolean x = a == b;
                    }
                }
                """);

        AnalyzeTaskRequest request = new AnalyzeTaskRequest(
                projectDir,
                Set.of("JAVA.STRING.EQUALITY"),
                AnalyzeMode.INCREMENTAL,
                Set.of(changed.getFileName()),
                Set.of(impacted.getFileName()),
                1024 * 1024,
                512 * 1024,
                2,
                Duration.ofSeconds(2),
                1,
                2,
                Duration.ofMillis(600),
                Set.of()
        );

        JavaAnalysisEngine engine = new JavaAnalysisEngine();
        AnalyzeTaskResult result = engine.analyze(request);
        assertEquals(2, result.stats().indexedFiles());
        assertTrue(result.issues().stream().anyMatch(issue -> issue.ruleId().equals("JAVA.STRING.EQUALITY")));
    }

    @Test
    void shouldIsolateRuleFailureWithoutBreakingTask() throws IOException {
        Path projectDir = Files.createTempDirectory("analysis-rule-isolation-test");
        Path javaFile = projectDir.resolve("Isolation.java");
        Files.writeString(javaFile, """
                class Isolation {
                    void run() {
                        String a = "1";
                        String b = "2";
                        boolean same = a == b;
                    }
                }
                """);

        JavaRule brokenRule = new JavaRule() {
            @Override
            public String id() {
                return "TEST.BROKEN_RULE";
            }

            @Override
            public String description() {
                return "always fail";
            }

            @Override
            public java.util.List<AnalysisIssue> analyze(CompilationUnit compilationUnit, RuleContext context) {
                throw new IllegalStateException("broken");
            }
        };

        JavaAnalysisEngine engine = new JavaAnalysisEngine(
                java.util.List.of(new com.project.javasecurityoptimizer.analysis.rules.StringEqualityRule(), brokenRule),
                new InMemoryRulePackLocalRepository()
        );

        AnalyzeTaskResult result = engine.analyze(new AnalyzeTaskRequest(
                projectDir,
                Set.of(),
                AnalyzeMode.FULL,
                Set.of(),
                Set.of(),
                1024 * 1024,
                512 * 1024,
                2,
                Duration.ofSeconds(2),
                1,
                2,
                Duration.ofMillis(600),
                Set.of()
        ));

        assertNotNull(result.ruleExecutionMetrics());
        assertTrue(result.issues().stream().anyMatch(issue -> issue.ruleId().equals("JAVA.STRING.EQUALITY")));
        assertEquals(1, result.stats().failedRuleCount());
    }

    @Test
    void shouldReportCacheHitsOnSecondRun() throws IOException {
        Path projectDir = Files.createTempDirectory("analysis-cache-test");
        Path javaFile = projectDir.resolve("CacheSample.java");
        Files.writeString(javaFile, """
                class CacheSample {
                    void run() {
                        String a = "1";
                        String b = "2";
                        boolean same = a == b;
                    }
                }
                """);

        JavaAnalysisEngine engine = new JavaAnalysisEngine();
        engine.analyze(AnalyzeTaskRequest.full(projectDir));
        AnalyzeTaskResult second = engine.analyze(AnalyzeTaskRequest.full(projectDir));

        assertTrue(second.executionReport().cacheStats().astCacheHits() > 0);
        assertTrue(second.executionReport().cacheStats().ruleCacheHits() > 0);
    }

    @Test
    void shouldUseDegradeRuleSetForLargeFile() throws IOException {
        Path projectDir = Files.createTempDirectory("analysis-degrade-test");
        Path javaFile = projectDir.resolve("Large.java");
        Files.writeString(javaFile, """
                class Large {
                    void run() {
                        String a = "1";
                        String b = "2";
                        boolean same = a == b;
                        int x = 42;
                    }
                }
                """);

        AnalyzeTaskRequest request = new AnalyzeTaskRequest(
                projectDir,
                Set.of("JAVA.STRING.EQUALITY", "JAVA.MAINTAINABILITY.MAGIC_NUMBER"),
                AnalyzeMode.FULL,
                Set.of(),
                Set.of(),
                1024 * 1024,
                1,
                2,
                Duration.ofSeconds(2),
                1,
                2,
                Duration.ofMillis(600),
                Set.of("JAVA.STRING.EQUALITY")
        );
        JavaAnalysisEngine engine = new JavaAnalysisEngine();
        AnalyzeTaskResult result = engine.analyze(request);
        Set<String> hitRules = result.issues().stream().map(AnalysisIssue::ruleId).collect(java.util.stream.Collectors.toSet());

        assertTrue(result.executionReport().degradedFiles().stream().anyMatch(file -> file.endsWith("Large.java")));
        assertTrue(hitRules.contains("JAVA.STRING.EQUALITY"));
        assertFalse(hitRules.contains("JAVA.MAINTAINABILITY.MAGIC_NUMBER"));
    }

    @Test
    void shouldParseLargeFileWithMemoryMappedRead() throws IOException {
        Path projectDir = Files.createTempDirectory("analysis-mmap-test");
        Path javaFile = projectDir.resolve("LargeMmap.java");
        StringBuilder builder = new StringBuilder();
        builder.append("class LargeMmap {\n");
        builder.append("    void run() {\n");
        builder.append("        String a = \"1\";\n");
        builder.append("        String b = \"2\";\n");
        builder.append("        boolean same = a == b;\n");
        builder.append("    }\n");
        for (int i = 0; i < 25_000; i++) {
            builder.append("    int field").append(i).append(" = ").append(i).append(";\n");
        }
        builder.append("}\n");
        Files.writeString(javaFile, builder.toString());

        JavaAnalysisEngine engine = new JavaAnalysisEngine();
        AnalyzeTaskRequest request = new AnalyzeTaskRequest(
                projectDir,
                Set.of("JAVA.STRING.EQUALITY"),
                AnalyzeMode.FULL,
                Set.of(),
                Set.of(),
                2 * 1024 * 1024L,
                256 * 1024L,
                4,
                Duration.ofSeconds(5),
                1,
                8,
                Duration.ofSeconds(2),
                Set.of("JAVA.STRING.EQUALITY")
        );
        AnalyzeTaskResult result = engine.analyze(request);

        assertEquals(1, result.stats().parsedFiles());
        assertTrue(result.issues().stream().anyMatch(issue -> issue.ruleId().equals("JAVA.STRING.EQUALITY")));
    }
}

package com.project.javasecurityoptimizer.analysis.hint;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContextAwareJitHintServiceTest {
    @Test
    void shouldBuildMultiLevelContextAndJitHints() throws IOException {
        Path projectDir = Files.createTempDirectory("jit-hint-test");
        Path file = projectDir.resolve("HotPath.java");
        Files.writeString(file, """
                class HotPath {
                    synchronized void tiny() { int x = 1; }
                    void hot() {
                        int sum = 0;
                        for (int i = 0; i < 10; i++) {
                            Integer v = Integer.valueOf(i);
                            sum += v.intValue();
                            Object obj = new Object();
                            String s = Class.forName("java.lang.String").getName();
                            try { sum += s.length(); } catch (Exception e) { sum++; }
                        }
                    }
                }
                """);

        ContextAwareJitHintService service = new ContextAwareJitHintService();
        ContextHintResponse response = service.analyze(new ContextHintRequest(projectDir.toString(), null, 50, 50));

        assertEquals(1, response.projectSummary().fileCount());
        assertFalse(response.fileSummaries().isEmpty());
        assertTrue(response.jitHints().stream().anyMatch(hint -> hint.hintId().equals("JIT.BOXING.IN_LOOP")));
        assertTrue(response.jitHints().stream().anyMatch(hint -> hint.hintId().equals("JIT.REFLECTION.HOT_PATH")));
        assertTrue(response.jitHints().stream().anyMatch(hint -> hint.hintId().equals("JIT.EXCEPTION.IN_LOOP")));
    }

    @Test
    void shouldRespectTargetFilesFilter() throws IOException {
        Path projectDir = Files.createTempDirectory("jit-hint-target-test");
        Path fileA = projectDir.resolve("A.java");
        Path fileB = projectDir.resolve("B.java");
        Files.writeString(fileA, "class A { void run() {} }");
        Files.writeString(fileB, "class B { void run() {} }");

        ContextAwareJitHintService service = new ContextAwareJitHintService();
        ContextHintResponse response = service.analyze(new ContextHintRequest(
                projectDir.toString(),
                List.of("A.java"),
                10,
                10
        ));

        assertEquals(1, response.fileSummaries().size());
        assertTrue(response.fileSummaries().getFirst().filePath().endsWith("A.java"));
    }
}

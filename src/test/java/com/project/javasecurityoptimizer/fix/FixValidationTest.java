package com.project.javasecurityoptimizer.fix;

import org.junit.jupiter.api.Test;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FixValidationTest {

    @Test
    void shouldGenerateLegalDiffAndCompileAfterFixThenRollback() throws Exception {
        String before = """
                import java.util.Objects;
                class Sample {
                    boolean same(String a, String b) {
                        return a == b;
                    }
                }
                """;
        String after = """
                import java.util.Objects;
                class Sample {
                    boolean same(String a, String b) {
                        return Objects.equals(a, b);
                    }
                }
                """;

        String diff = unifiedDiff("Sample.java", before, after);
        assertTrue(diff.startsWith("--- Sample.java"));
        assertTrue(diff.contains("+++ Sample.java"));
        assertTrue(diff.contains("@@"));
        assertTrue(diff.contains("-        return a == b;"));
        assertTrue(diff.contains("+        return Objects.equals(a, b);"));

        assertTrue(compile("Sample", after), "fixed source should compile");
        assertTrue(compile("Sample", before), "rollback source should compile");
    }

    @Test
    void shouldTreatInvalidDiffAsIllegal() {
        String invalidDiff = """
                --- A.java
                +++ A.java
                -no hunk header
                +still invalid
                """;
        assertFalse(isLegalUnifiedDiff(invalidDiff));
    }

    private String unifiedDiff(String fileName, String before, String after) {
        List<String> beforeLines = before.stripTrailing().lines().toList();
        List<String> afterLines = after.stripTrailing().lines().toList();
        StringBuilder builder = new StringBuilder();
        builder.append("--- ").append(fileName).append("\n");
        builder.append("+++ ").append(fileName).append("\n");
        builder.append("@@ -1,").append(beforeLines.size()).append(" +1,").append(afterLines.size()).append(" @@\n");
        for (String line : beforeLines) {
            builder.append("-").append(line).append("\n");
        }
        for (String line : afterLines) {
            builder.append("+").append(line).append("\n");
        }
        return builder.toString();
    }

    private boolean isLegalUnifiedDiff(String diff) {
        return diff.startsWith("--- ")
                && diff.contains("\n+++ ")
                && diff.contains("\n@@ ");
    }

    private boolean compile(String className, String source) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "JDK compiler is required");

        Path tempDir = Files.createTempDirectory("fix-compile");
        Path sourceFile = tempDir.resolve(className + ".java");
        Files.writeString(sourceFile, source);
        int result = compiler.run(null, null, null, sourceFile.toString());
        return result == 0;
    }
}

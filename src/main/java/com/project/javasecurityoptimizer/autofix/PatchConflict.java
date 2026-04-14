package com.project.javasecurityoptimizer.autofix;

import java.nio.file.Path;

public record PatchConflict(
        Path filePath,
        int startLine,
        int endLine,
        String reason
) {
}

package com.project.javasecurityoptimizer.autofix;

import java.nio.file.Path;
import java.util.List;

public record PatchPreview(
        Path filePath,
        List<String> diffLines
) {
    public PatchPreview {
        diffLines = diffLines == null ? List.of() : List.copyOf(diffLines);
    }
}

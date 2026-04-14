package com.project.javasecurityoptimizer.autofix;

import com.project.javasecurityoptimizer.analysis.FixSafetyLevel;

import java.nio.file.Path;
import java.util.Objects;

public record PatchEdit(
        Path filePath,
        int startLine,
        int endLine,
        String replacement,
        String originalSnippet,
        String ruleId,
        FixSafetyLevel safetyLevel
) {
    public PatchEdit {
        Objects.requireNonNull(filePath, "filePath must not be null");
        if (startLine <= 0 || endLine <= 0 || endLine < startLine) {
            throw new IllegalArgumentException("invalid edit range");
        }
        replacement = replacement == null ? "" : replacement;
        originalSnippet = originalSnippet == null ? "" : originalSnippet;
        ruleId = ruleId == null ? "UNKNOWN" : ruleId;
        safetyLevel = safetyLevel == null ? FixSafetyLevel.REVIEW_REQUIRED : safetyLevel;
    }
}

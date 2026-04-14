package com.project.javasecurityoptimizer.analysis;

import java.nio.file.Path;
import java.util.List;

public record AnalysisIssue(
        String ruleId,
        String message,
        Path filePath,
        int line,
        IssueSeverity severity,
        List<FixCandidate> fixCandidates
) {
}

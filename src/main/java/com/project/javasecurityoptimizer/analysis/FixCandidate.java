package com.project.javasecurityoptimizer.analysis;

public record FixCandidate(
        String title,
        String suggestion,
        FixSafetyLevel safetyLevel
) {
}

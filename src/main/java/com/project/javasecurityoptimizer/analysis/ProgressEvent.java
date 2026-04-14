package com.project.javasecurityoptimizer.analysis;

import java.time.Instant;

public record ProgressEvent(
        String stage,
        int percentage,
        String currentFile,
        String message,
        Instant timestamp
) {
    public static ProgressEvent of(String stage, int percentage, String currentFile, String message) {
        int bounded = Math.max(0, Math.min(100, percentage));
        return new ProgressEvent(stage, bounded, currentFile, message, Instant.now());
    }

    public static ProgressEvent of(String stage, int percentage, String message) {
        return of(stage, percentage, null, message);
    }

    public static ProgressEvent of(String stage, String message) {
        return of(stage, 0, null, message);
    }
}

package com.project.javasecurityoptimizer.analysis.hint;

import java.util.List;

public record ContextHintRequest(
        String projectPath,
        List<String> targetFiles,
        Integer maxFiles,
        Integer maxMethodsPerFile
) {
}

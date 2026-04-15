package com.project.javasecurityoptimizer.analysis.hint;

import java.util.List;

public record FileContextSummary(
        String filePath,
        int classCount,
        int methodCount,
        int loopCount,
        int branchCount,
        List<ClassContextSummary> classes,
        List<MethodContextSummary> methods
) {
    public FileContextSummary {
        classes = classes == null ? List.of() : List.copyOf(classes);
        methods = methods == null ? List.of() : List.copyOf(methods);
    }
}

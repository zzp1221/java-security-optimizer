package com.project.javasecurityoptimizer.analysis;

import com.github.javaparser.ast.CompilationUnit;

import java.nio.file.Path;

public record ParsedSource(Path filePath, CompilationUnit compilationUnit) {
}

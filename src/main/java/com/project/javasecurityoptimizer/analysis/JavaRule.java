package com.project.javasecurityoptimizer.analysis;

import com.github.javaparser.ast.CompilationUnit;

import java.util.List;

public interface JavaRule {
    String id();

    String description();

    List<AnalysisIssue> analyze(CompilationUnit compilationUnit, RuleContext context);
}

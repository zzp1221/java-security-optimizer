package com.project.javasecurityoptimizer.analysis.rules;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.stmt.IfStmt;
import com.project.javasecurityoptimizer.analysis.AnalysisIssue;
import com.project.javasecurityoptimizer.analysis.IssueSeverity;
import com.project.javasecurityoptimizer.analysis.RuleContext;

import java.util.ArrayList;
import java.util.List;

public final class DeepNestingRule extends AbstractJavaRule {
    private static final int MAX_NESTING = 3;

    @Override
    public String id() {
        return "JAVA.MAINTAINABILITY.DEEP_NESTING";
    }

    @Override
    public String description() {
        return "过深嵌套影响可读性";
    }

    @Override
    public List<AnalysisIssue> analyze(CompilationUnit compilationUnit, RuleContext context) {
        List<AnalysisIssue> issues = new ArrayList<>();
        for (IfStmt ifStmt : compilationUnit.findAll(IfStmt.class)) {
            int depth = nestingDepth(ifStmt);
            if (depth > MAX_NESTING) {
                issues.add(issue(context, ifStmt, IssueSeverity.MEDIUM, "if 嵌套层级过深: " + depth));
            }
        }
        return issues;
    }

    private int nestingDepth(IfStmt stmt) {
        int depth = 0;
        com.github.javaparser.ast.Node current = stmt;
        while (current != null) {
            if (current instanceof IfStmt) {
                depth++;
            }
            current = current.getParentNode().orElse(null);
        }
        return depth;
    }
}

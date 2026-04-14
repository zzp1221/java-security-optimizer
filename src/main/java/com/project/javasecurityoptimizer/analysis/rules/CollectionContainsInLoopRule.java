package com.project.javasecurityoptimizer.analysis.rules;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.project.javasecurityoptimizer.analysis.AnalysisIssue;
import com.project.javasecurityoptimizer.analysis.IssueSeverity;
import com.project.javasecurityoptimizer.analysis.RuleContext;

import java.util.ArrayList;
import java.util.List;

public final class CollectionContainsInLoopRule extends AbstractJavaRule {
    @Override
    public String id() {
        return "JAVA.PERF.CONTAINS_IN_LOOP";
    }

    @Override
    public String description() {
        return "循环内频繁 contains 可能导致性能问题";
    }

    @Override
    public List<AnalysisIssue> analyze(CompilationUnit compilationUnit, RuleContext context) {
        List<AnalysisIssue> issues = new ArrayList<>();
        for (MethodCallExpr call : compilationUnit.findAll(MethodCallExpr.class)) {
            if (!call.getNameAsString().equals("contains")) {
                continue;
            }
            if (isInsideLoop(call)) {
                issues.add(issue(context, call, IssueSeverity.MEDIUM, "循环内 contains 调用建议评估 Set/Map 替代"));
            }
        }
        return issues;
    }

    private boolean isInsideLoop(Node node) {
        return node.findAncestor(com.github.javaparser.ast.stmt.ForStmt.class).isPresent()
                || node.findAncestor(com.github.javaparser.ast.stmt.ForEachStmt.class).isPresent()
                || node.findAncestor(com.github.javaparser.ast.stmt.WhileStmt.class).isPresent()
                || node.findAncestor(com.github.javaparser.ast.stmt.DoStmt.class).isPresent();
    }
}

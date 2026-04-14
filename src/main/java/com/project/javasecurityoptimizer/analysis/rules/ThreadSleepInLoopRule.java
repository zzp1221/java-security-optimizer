package com.project.javasecurityoptimizer.analysis.rules;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.project.javasecurityoptimizer.analysis.AnalysisIssue;
import com.project.javasecurityoptimizer.analysis.IssueSeverity;
import com.project.javasecurityoptimizer.analysis.RuleContext;

import java.util.ArrayList;
import java.util.List;

public final class ThreadSleepInLoopRule extends AbstractJavaRule {
    @Override
    public String id() {
        return "JAVA.CONCURRENCY.SLEEP_IN_LOOP";
    }

    @Override
    public String description() {
        return "避免循环中调用 Thread.sleep";
    }

    @Override
    public List<AnalysisIssue> analyze(CompilationUnit compilationUnit, RuleContext context) {
        List<AnalysisIssue> issues = new ArrayList<>();
        for (MethodCallExpr methodCallExpr : compilationUnit.findAll(MethodCallExpr.class)) {
            if (!methodCallExpr.getNameAsString().equals("sleep")) {
                continue;
            }
            if (methodCallExpr.getScope().isEmpty() || !methodCallExpr.getScope().get().toString().equals("Thread")) {
                continue;
            }
            if (isInsideLoop(methodCallExpr)) {
                issues.add(issue(context, methodCallExpr, IssueSeverity.MEDIUM,
                        "循环中调用 Thread.sleep 可能导致吞吐下降"));
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

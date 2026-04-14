package com.project.javasecurityoptimizer.analysis.rules;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.project.javasecurityoptimizer.analysis.AnalysisIssue;
import com.project.javasecurityoptimizer.analysis.IssueSeverity;
import com.project.javasecurityoptimizer.analysis.RuleContext;

import java.util.ArrayList;
import java.util.List;

public final class BlockingCallInParallelStreamRule extends AbstractJavaRule {
    @Override
    public String id() {
        return "JAVA.PERF.BLOCKING_IN_PARALLEL_STREAM";
    }

    @Override
    public String description() {
        return "parallelStream 中不建议阻塞调用";
    }

    @Override
    public List<AnalysisIssue> analyze(CompilationUnit compilationUnit, RuleContext context) {
        List<AnalysisIssue> issues = new ArrayList<>();
        for (MethodCallExpr call : compilationUnit.findAll(MethodCallExpr.class)) {
            if (!call.getNameAsString().equals("sleep")) {
                continue;
            }
            boolean inParallelStream = call.findAncestor(MethodCallExpr.class)
                    .stream()
                    .anyMatch(parent -> parent.getNameAsString().equals("parallelStream"));
            if (inParallelStream) {
                issues.add(issue(context, call, IssueSeverity.MEDIUM, "parallelStream 流程中存在阻塞调用"));
            }
        }
        return issues;
    }
}

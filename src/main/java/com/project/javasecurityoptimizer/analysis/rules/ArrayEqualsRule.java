package com.project.javasecurityoptimizer.analysis.rules;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.project.javasecurityoptimizer.analysis.AnalysisIssue;
import com.project.javasecurityoptimizer.analysis.IssueSeverity;
import com.project.javasecurityoptimizer.analysis.RuleContext;

import java.util.ArrayList;
import java.util.List;

public final class ArrayEqualsRule extends AbstractJavaRule {
    @Override
    public String id() {
        return "JAVA.RELIABILITY.ARRAY_EQUALS";
    }

    @Override
    public String description() {
        return "数组比较应使用 Arrays.equals/deepEquals";
    }

    @Override
    public List<AnalysisIssue> analyze(CompilationUnit compilationUnit, RuleContext context) {
        List<AnalysisIssue> issues = new ArrayList<>();
        for (MethodCallExpr call : compilationUnit.findAll(MethodCallExpr.class)) {
            if (call.getNameAsString().equals("equals") && call.getArguments().size() == 1) {
                String owner = call.getScope().map(Object::toString).orElse("");
                if (owner.toLowerCase().contains("arr") || owner.toLowerCase().contains("array")) {
                    issues.add(issue(context, call, IssueSeverity.MEDIUM, "疑似数组直接 equals 比较"));
                }
            }
        }
        return issues;
    }
}

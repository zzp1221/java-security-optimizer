package com.project.javasecurityoptimizer.analysis.rules;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.project.javasecurityoptimizer.analysis.AnalysisIssue;
import com.project.javasecurityoptimizer.analysis.IssueSeverity;
import com.project.javasecurityoptimizer.analysis.RuleContext;

import java.util.ArrayList;
import java.util.List;

public final class OptionalGetWithoutIsPresentRule extends AbstractJavaRule {
    @Override
    public String id() {
        return "JAVA.RELIABILITY.OPTIONAL_GET_WITHOUT_CHECK";
    }

    @Override
    public String description() {
        return "Optional.get 调用前应先判断 isPresent";
    }

    @Override
    public List<AnalysisIssue> analyze(CompilationUnit compilationUnit, RuleContext context) {
        List<AnalysisIssue> issues = new ArrayList<>();
        for (MethodCallExpr call : compilationUnit.findAll(MethodCallExpr.class)) {
            if (!call.getNameAsString().equals("get") || call.getScope().isEmpty()) {
                continue;
            }
            String scope = call.getScope().get().toString();
            boolean hasGuard = call.findAncestor(com.github.javaparser.ast.stmt.IfStmt.class)
                    .map(ifStmt -> ifStmt.getCondition().toString().contains(scope + ".isPresent()"))
                    .orElse(false);
            if (!hasGuard) {
                issues.add(issue(context, call, IssueSeverity.HIGH, "Optional.get 可能触发 NoSuchElementException"));
            }
        }
        return issues;
    }
}

package com.project.javasecurityoptimizer.analysis.rules;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.BooleanLiteralExpr;
import com.project.javasecurityoptimizer.analysis.AnalysisIssue;
import com.project.javasecurityoptimizer.analysis.IssueSeverity;
import com.project.javasecurityoptimizer.analysis.RuleContext;

import java.util.ArrayList;
import java.util.List;

public final class BooleanLiteralComparisonRule extends AbstractJavaRule {
    @Override
    public String id() {
        return "JAVA.MAINTAINABILITY.BOOLEAN_LITERAL_COMPARISON";
    }

    @Override
    public String description() {
        return "避免与 true/false 直接比较";
    }

    @Override
    public List<AnalysisIssue> analyze(CompilationUnit compilationUnit, RuleContext context) {
        List<AnalysisIssue> issues = new ArrayList<>();
        for (BinaryExpr binaryExpr : compilationUnit.findAll(BinaryExpr.class)) {
            if (!(binaryExpr.getOperator() == BinaryExpr.Operator.EQUALS
                    || binaryExpr.getOperator() == BinaryExpr.Operator.NOT_EQUALS)) {
                continue;
            }
            if (binaryExpr.getLeft() instanceof BooleanLiteralExpr || binaryExpr.getRight() instanceof BooleanLiteralExpr) {
                issues.add(issue(context, binaryExpr, IssueSeverity.LOW, "与布尔字面量比较可简化为直接判断"));
            }
        }
        return issues;
    }
}

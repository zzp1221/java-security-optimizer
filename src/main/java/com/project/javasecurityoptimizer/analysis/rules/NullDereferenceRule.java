package com.project.javasecurityoptimizer.analysis.rules;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.NullLiteralExpr;
import com.github.javaparser.ast.stmt.IfStmt;
import com.project.javasecurityoptimizer.analysis.AnalysisIssue;
import com.project.javasecurityoptimizer.analysis.IssueSeverity;
import com.project.javasecurityoptimizer.analysis.RuleContext;

import java.util.ArrayList;
import java.util.List;

public final class NullDereferenceRule extends AbstractJavaRule {
    @Override
    public String id() {
        return "JAVA.NPE.NULL_DEREFERENCE";
    }

    @Override
    public String description() {
        return "识别 null 分支中的对象调用";
    }

    @Override
    public List<AnalysisIssue> analyze(CompilationUnit compilationUnit, RuleContext context) {
        List<AnalysisIssue> issues = new ArrayList<>();
        for (IfStmt ifStmt : compilationUnit.findAll(IfStmt.class)) {
            String varName = nullCheckedName(ifStmt.getCondition());
            if (varName == null) {
                continue;
            }
            for (MethodCallExpr methodCallExpr : ifStmt.getThenStmt().findAll(MethodCallExpr.class)) {
                if (methodCallExpr.getScope().isPresent()
                        && methodCallExpr.getScope().get() instanceof NameExpr nameExpr
                        && nameExpr.getNameAsString().equals(varName)) {
                    issues.add(issue(context, methodCallExpr, IssueSeverity.CRITICAL,
                            "检测到空值分支中对对象 " + varName + " 的方法调用"));
                }
            }
        }
        return issues;
    }

    private String nullCheckedName(Expression expression) {
        if (!(expression instanceof BinaryExpr binaryExpr)) {
            return null;
        }
        if (binaryExpr.getOperator() != BinaryExpr.Operator.EQUALS) {
            return null;
        }
        if (binaryExpr.getLeft() instanceof NameExpr nameExpr && binaryExpr.getRight() instanceof NullLiteralExpr) {
            return nameExpr.getNameAsString();
        }
        if (binaryExpr.getRight() instanceof NameExpr nameExpr && binaryExpr.getLeft() instanceof NullLiteralExpr) {
            return nameExpr.getNameAsString();
        }
        return null;
    }
}

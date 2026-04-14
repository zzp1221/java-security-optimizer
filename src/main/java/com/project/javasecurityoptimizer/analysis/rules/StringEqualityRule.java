package com.project.javasecurityoptimizer.analysis.rules;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.project.javasecurityoptimizer.analysis.AnalysisIssue;
import com.project.javasecurityoptimizer.analysis.FixCandidate;
import com.project.javasecurityoptimizer.analysis.FixSafetyLevel;
import com.project.javasecurityoptimizer.analysis.IssueSeverity;
import com.project.javasecurityoptimizer.analysis.RuleContext;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class StringEqualityRule extends AbstractJavaRule {
    @Override
    public String id() {
        return "JAVA.STRING.EQUALITY";
    }

    @Override
    public String description() {
        return "禁止使用 == 或 != 比较字符串内容";
    }

    @Override
    public List<AnalysisIssue> analyze(CompilationUnit compilationUnit, RuleContext context) {
        List<AnalysisIssue> issues = new ArrayList<>();
        Set<String> knownStringNames = collectKnownStringNames(compilationUnit);
        for (BinaryExpr binaryExpr : compilationUnit.findAll(BinaryExpr.class)) {
            if (!(binaryExpr.getOperator() == BinaryExpr.Operator.EQUALS
                    || binaryExpr.getOperator() == BinaryExpr.Operator.NOT_EQUALS)) {
                continue;
            }
            if (!looksLikeStringComparison(binaryExpr.getLeft(), binaryExpr.getRight(), knownStringNames)) {
                continue;
            }
            String left = binaryExpr.getLeft().toString();
            String right = binaryExpr.getRight().toString();
            FixCandidate fix = new FixCandidate(
                    "改为 Objects.equals 比较",
                    "使用 java.util.Objects.equals(" + left + ", " + right + ") 替代引用比较",
                    FixSafetyLevel.SAFE
            );
            issues.add(issue(context, binaryExpr, IssueSeverity.HIGH,
                    "字符串使用了引用比较，可能导致逻辑错误", List.of(fix)));
        }
        return issues;
    }

    private boolean looksLikeStringComparison(Expression left, Expression right, Set<String> knownStringNames) {
        return left instanceof StringLiteralExpr
                || right instanceof StringLiteralExpr
                || left.toString().contains("\"")
                || right.toString().contains("\"")
                || isKnownStringName(left, knownStringNames)
                || isKnownStringName(right, knownStringNames);
    }

    private Set<String> collectKnownStringNames(CompilationUnit compilationUnit) {
        Set<String> names = new HashSet<>();
        for (VariableDeclarator variable : compilationUnit.findAll(VariableDeclarator.class)) {
            if ("String".equals(variable.getTypeAsString())) {
                names.add(variable.getNameAsString());
            }
        }
        for (Parameter parameter : compilationUnit.findAll(Parameter.class)) {
            if ("String".equals(parameter.getTypeAsString())) {
                names.add(parameter.getNameAsString());
            }
        }
        return names;
    }

    private boolean isKnownStringName(Expression expression, Set<String> knownStringNames) {
        return expression instanceof NameExpr nameExpr
                && knownStringNames.contains(nameExpr.getNameAsString());
    }
}

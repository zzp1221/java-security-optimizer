package com.project.javasecurityoptimizer.analysis.rules;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.project.javasecurityoptimizer.analysis.AnalysisIssue;
import com.project.javasecurityoptimizer.analysis.IssueSeverity;
import com.project.javasecurityoptimizer.analysis.RuleContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class MagicNumberRule extends AbstractJavaRule {
    private static final Set<Integer> ALLOWED = Set.of(-1, 0, 1, 2);

    @Override
    public String id() {
        return "JAVA.MAINTAINABILITY.MAGIC_NUMBER";
    }

    @Override
    public String description() {
        return "建议避免魔法数字";
    }

    @Override
    public List<AnalysisIssue> analyze(CompilationUnit compilationUnit, RuleContext context) {
        List<AnalysisIssue> issues = new ArrayList<>();
        for (IntegerLiteralExpr literal : compilationUnit.findAll(IntegerLiteralExpr.class)) {
            try {
                int value = Integer.parseInt(literal.getValue().replace("_", ""));
                if (!ALLOWED.contains(value)) {
                    issues.add(issue(context, literal, IssueSeverity.LOW, "疑似魔法数字: " + value));
                }
            } catch (NumberFormatException ignored) {
                // ignore non-int literal
            }
        }
        return issues;
    }
}

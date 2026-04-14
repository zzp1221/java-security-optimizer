package com.project.javasecurityoptimizer.analysis.rules;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.project.javasecurityoptimizer.analysis.AnalysisIssue;
import com.project.javasecurityoptimizer.analysis.IssueSeverity;
import com.project.javasecurityoptimizer.analysis.RuleContext;

import java.util.ArrayList;
import java.util.List;

public final class HardcodedCredentialRule extends AbstractJavaRule {
    @Override
    public String id() {
        return "JAVA.SECURITY.HARDCODED_CREDENTIAL";
    }

    @Override
    public String description() {
        return "识别硬编码凭据";
    }

    @Override
    public List<AnalysisIssue> analyze(CompilationUnit compilationUnit, RuleContext context) {
        List<AnalysisIssue> issues = new ArrayList<>();
        for (VariableDeclarator variable : compilationUnit.findAll(VariableDeclarator.class)) {
            if (variable.getInitializer().isEmpty()
                    || !(variable.getInitializer().get() instanceof StringLiteralExpr literalExpr)) {
                continue;
            }
            String name = variable.getNameAsString().toLowerCase();
            if ((name.contains("password") || name.contains("secret") || name.contains("token"))
                    && !literalExpr.asString().isBlank()) {
                issues.add(issue(context, variable, IssueSeverity.CRITICAL,
                        "变量 " + variable.getNameAsString() + " 疑似硬编码敏感信息"));
            }
        }
        return issues;
    }
}

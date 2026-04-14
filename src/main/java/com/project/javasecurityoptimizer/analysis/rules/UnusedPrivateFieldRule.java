package com.project.javasecurityoptimizer.analysis.rules;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.NameExpr;
import com.project.javasecurityoptimizer.analysis.AnalysisIssue;
import com.project.javasecurityoptimizer.analysis.IssueSeverity;
import com.project.javasecurityoptimizer.analysis.RuleContext;

import java.util.ArrayList;
import java.util.List;

public final class UnusedPrivateFieldRule extends AbstractJavaRule {
    @Override
    public String id() {
        return "JAVA.MAINTAINABILITY.UNUSED_PRIVATE_FIELD";
    }

    @Override
    public String description() {
        return "检测未使用的 private 字段";
    }

    @Override
    public List<AnalysisIssue> analyze(CompilationUnit compilationUnit, RuleContext context) {
        List<AnalysisIssue> issues = new ArrayList<>();
        for (FieldDeclaration field : compilationUnit.findAll(FieldDeclaration.class)) {
            if (!field.isPrivate()) {
                continue;
            }
            for (VariableDeclarator variable : field.getVariables()) {
                String fieldName = variable.getNameAsString();
                long references = compilationUnit.findAll(NameExpr.class).stream()
                        .filter(nameExpr -> nameExpr.getNameAsString().equals(fieldName))
                        .count();
                if (references <= 1) {
                    issues.add(issue(context, variable, IssueSeverity.LOW, "private 字段未被使用: " + fieldName));
                }
            }
        }
        return issues;
    }
}

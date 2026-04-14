package com.project.javasecurityoptimizer.analysis.rules;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.project.javasecurityoptimizer.analysis.AnalysisIssue;
import com.project.javasecurityoptimizer.analysis.IssueSeverity;
import com.project.javasecurityoptimizer.analysis.RuleContext;

import java.util.ArrayList;
import java.util.List;

public final class LongMethodRule extends AbstractJavaRule {
    private static final int MAX_STATEMENTS = 40;

    @Override
    public String id() {
        return "JAVA.MAINTAINABILITY.LONG_METHOD";
    }

    @Override
    public String description() {
        return "方法体过长会降低可维护性";
    }

    @Override
    public List<AnalysisIssue> analyze(CompilationUnit compilationUnit, RuleContext context) {
        List<AnalysisIssue> issues = new ArrayList<>();
        for (MethodDeclaration method : compilationUnit.findAll(MethodDeclaration.class)) {
            int statementCount = method.getBody().map(body -> body.getStatements().size()).orElse(0);
            if (statementCount > MAX_STATEMENTS) {
                issues.add(issue(context, method, IssueSeverity.MEDIUM,
                        "方法 " + method.getNameAsString() + " 语句数为 " + statementCount + "，建议拆分"));
            }
        }
        return issues;
    }
}

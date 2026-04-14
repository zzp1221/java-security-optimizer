package com.project.javasecurityoptimizer.analysis.rules;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.project.javasecurityoptimizer.analysis.AnalysisIssue;
import com.project.javasecurityoptimizer.analysis.IssueSeverity;
import com.project.javasecurityoptimizer.analysis.RuleContext;

import java.util.ArrayList;
import java.util.List;

public final class TooManyParametersRule extends AbstractJavaRule {
    private static final int MAX_PARAMS = 5;

    @Override
    public String id() {
        return "JAVA.MAINTAINABILITY.TOO_MANY_PARAMETERS";
    }

    @Override
    public String description() {
        return "方法参数过多会降低可读性";
    }

    @Override
    public List<AnalysisIssue> analyze(CompilationUnit compilationUnit, RuleContext context) {
        List<AnalysisIssue> issues = new ArrayList<>();
        for (MethodDeclaration method : compilationUnit.findAll(MethodDeclaration.class)) {
            if (method.getParameters().size() > MAX_PARAMS) {
                issues.add(issue(context, method, IssueSeverity.MEDIUM,
                        "方法 " + method.getNameAsString() + " 参数过多: " + method.getParameters().size()));
            }
        }
        return issues;
    }
}

package com.project.javasecurityoptimizer.analysis.rules;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.project.javasecurityoptimizer.analysis.AnalysisIssue;
import com.project.javasecurityoptimizer.analysis.IssueSeverity;
import com.project.javasecurityoptimizer.analysis.RuleContext;

import java.util.ArrayList;
import java.util.List;

public final class MultipleReturnRule extends AbstractJavaRule {
    @Override
    public String id() {
        return "JAVA.MAINTAINABILITY.MULTIPLE_RETURN";
    }

    @Override
    public String description() {
        return "复杂方法中过多 return 会降低可读性";
    }

    @Override
    public List<AnalysisIssue> analyze(CompilationUnit compilationUnit, RuleContext context) {
        List<AnalysisIssue> issues = new ArrayList<>();
        for (MethodDeclaration method : compilationUnit.findAll(MethodDeclaration.class)) {
            int returns = method.findAll(ReturnStmt.class).size();
            if (returns > 3) {
                issues.add(issue(context, method, IssueSeverity.LOW,
                        "方法 " + method.getNameAsString() + " 有 " + returns + " 个 return"));
            }
        }
        return issues;
    }
}

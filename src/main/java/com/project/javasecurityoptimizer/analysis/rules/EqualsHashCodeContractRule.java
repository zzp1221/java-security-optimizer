package com.project.javasecurityoptimizer.analysis.rules;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.project.javasecurityoptimizer.analysis.AnalysisIssue;
import com.project.javasecurityoptimizer.analysis.IssueSeverity;
import com.project.javasecurityoptimizer.analysis.RuleContext;

import java.util.ArrayList;
import java.util.List;

public final class EqualsHashCodeContractRule extends AbstractJavaRule {
    @Override
    public String id() {
        return "JAVA.MAINTAINABILITY.EQUALS_HASHCODE_CONTRACT";
    }

    @Override
    public String description() {
        return "重写 equals 时建议同时重写 hashCode";
    }

    @Override
    public List<AnalysisIssue> analyze(CompilationUnit compilationUnit, RuleContext context) {
        List<AnalysisIssue> issues = new ArrayList<>();
        for (ClassOrInterfaceDeclaration clazz : compilationUnit.findAll(ClassOrInterfaceDeclaration.class)) {
            boolean overridesEquals = hasMethod(clazz, "equals", 1);
            boolean overridesHashCode = hasMethod(clazz, "hashCode", 0);
            if (overridesEquals && !overridesHashCode) {
                issues.add(issue(context, clazz, IssueSeverity.MEDIUM, "类 " + clazz.getNameAsString() + " 重写 equals 但未重写 hashCode"));
            }
        }
        return issues;
    }

    private boolean hasMethod(ClassOrInterfaceDeclaration clazz, String name, int params) {
        for (MethodDeclaration method : clazz.getMethodsByName(name)) {
            if (method.getParameters().size() == params) {
                return true;
            }
        }
        return false;
    }
}

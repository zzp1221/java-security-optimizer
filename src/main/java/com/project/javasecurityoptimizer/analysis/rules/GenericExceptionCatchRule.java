package com.project.javasecurityoptimizer.analysis.rules;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.stmt.CatchClause;
import com.project.javasecurityoptimizer.analysis.AnalysisIssue;
import com.project.javasecurityoptimizer.analysis.IssueSeverity;
import com.project.javasecurityoptimizer.analysis.RuleContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class GenericExceptionCatchRule extends AbstractJavaRule {
    private static final Set<String> GENERIC_TYPES = Set.of("Exception", "Throwable");

    @Override
    public String id() {
        return "JAVA.EXCEPTION.GENERIC_CATCH";
    }

    @Override
    public String description() {
        return "避免捕获过于宽泛的异常类型";
    }

    @Override
    public List<AnalysisIssue> analyze(CompilationUnit compilationUnit, RuleContext context) {
        List<AnalysisIssue> issues = new ArrayList<>();
        for (CatchClause catchClause : compilationUnit.findAll(CatchClause.class)) {
            String typeName = catchClause.getParameter().getType().asString();
            if (GENERIC_TYPES.contains(typeName)) {
                issues.add(issue(context, catchClause, IssueSeverity.MEDIUM,
                        "捕获了过于宽泛的异常类型 " + typeName + "，建议收窄范围"));
            }
        }
        return issues;
    }
}

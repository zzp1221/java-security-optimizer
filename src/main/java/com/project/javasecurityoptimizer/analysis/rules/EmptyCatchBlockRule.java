package com.project.javasecurityoptimizer.analysis.rules;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.stmt.CatchClause;
import com.project.javasecurityoptimizer.analysis.AnalysisIssue;
import com.project.javasecurityoptimizer.analysis.IssueSeverity;
import com.project.javasecurityoptimizer.analysis.RuleContext;

import java.util.ArrayList;
import java.util.List;

public final class EmptyCatchBlockRule extends AbstractJavaRule {
    @Override
    public String id() {
        return "JAVA.EXCEPTION.EMPTY_CATCH";
    }

    @Override
    public String description() {
        return "禁止空 catch 块吞掉异常";
    }

    @Override
    public List<AnalysisIssue> analyze(CompilationUnit compilationUnit, RuleContext context) {
        List<AnalysisIssue> issues = new ArrayList<>();
        for (CatchClause catchClause : compilationUnit.findAll(CatchClause.class)) {
            if (catchClause.getBody().getStatements().isEmpty()) {
                issues.add(issue(context, catchClause, IssueSeverity.HIGH, "空 catch 块会隐藏异常"));
            }
        }
        return issues;
    }
}

package com.project.javasecurityoptimizer.analysis.rules;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.AssignExpr;
import com.project.javasecurityoptimizer.analysis.AnalysisIssue;
import com.project.javasecurityoptimizer.analysis.FixCandidate;
import com.project.javasecurityoptimizer.analysis.FixSafetyLevel;
import com.project.javasecurityoptimizer.analysis.IssueSeverity;
import com.project.javasecurityoptimizer.analysis.RuleContext;

import java.util.ArrayList;
import java.util.List;

public final class LoopStringConcatenationRule extends AbstractJavaRule {
    @Override
    public String id() {
        return "JAVA.PERF.LOOP_STRING_CONCAT";
    }

    @Override
    public String description() {
        return "循环内字符串拼接应替换为 StringBuilder";
    }

    @Override
    public List<AnalysisIssue> analyze(CompilationUnit compilationUnit, RuleContext context) {
        List<AnalysisIssue> issues = new ArrayList<>();
        for (AssignExpr assignExpr : compilationUnit.findAll(AssignExpr.class)) {
            if (assignExpr.getOperator() != AssignExpr.Operator.PLUS) {
                continue;
            }
            if (!isInsideLoop(assignExpr)) {
                continue;
            }
            FixCandidate fix = new FixCandidate(
                    "改用 StringBuilder",
                    "将循环内 += 拼接改为 StringBuilder.append()，循环结束后再 toString()",
                    FixSafetyLevel.REVIEW_REQUIRED
            );
            issues.add(issue(context, assignExpr, IssueSeverity.MEDIUM,
                    "循环内字符串拼接可能造成大量临时对象", List.of(fix)));
        }
        return issues;
    }

    private boolean isInsideLoop(Node node) {
        return node.findAncestor(com.github.javaparser.ast.stmt.ForStmt.class).isPresent()
                || node.findAncestor(com.github.javaparser.ast.stmt.ForEachStmt.class).isPresent()
                || node.findAncestor(com.github.javaparser.ast.stmt.WhileStmt.class).isPresent()
                || node.findAncestor(com.github.javaparser.ast.stmt.DoStmt.class).isPresent();
    }
}

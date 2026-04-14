package com.project.javasecurityoptimizer.analysis.rules;

import com.github.javaparser.ast.Node;
import com.project.javasecurityoptimizer.analysis.AnalysisIssue;
import com.project.javasecurityoptimizer.analysis.FixCandidate;
import com.project.javasecurityoptimizer.analysis.IssueSeverity;
import com.project.javasecurityoptimizer.analysis.JavaRule;
import com.project.javasecurityoptimizer.analysis.RuleContext;

import java.util.List;

public abstract class AbstractJavaRule implements JavaRule {
    protected AnalysisIssue issue(
            RuleContext context,
            Node node,
            IssueSeverity severity,
            String message
    ) {
        return issue(context, node, severity, message, List.of());
    }

    protected AnalysisIssue issue(
            RuleContext context,
            Node node,
            IssueSeverity severity,
            String message,
            List<FixCandidate> fixCandidates
    ) {
        int line = node.getRange().map(range -> range.begin.line).orElse(-1);
        return new AnalysisIssue(id(), message, context.filePath(), line, severity, fixCandidates);
    }
}

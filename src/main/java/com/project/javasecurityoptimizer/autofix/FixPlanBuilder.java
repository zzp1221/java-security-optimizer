package com.project.javasecurityoptimizer.autofix;

import com.project.javasecurityoptimizer.analysis.AnalysisIssue;
import com.project.javasecurityoptimizer.analysis.FixCandidate;
import com.project.javasecurityoptimizer.analysis.FixSafetyLevel;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

@Component
public class FixPlanBuilder {
    public List<FixPlanItem> buildFromIssues(List<AnalysisIssue> issues) {
        List<FixPlanItem> items = new ArrayList<>();
        if (issues == null) {
            return items;
        }
        for (AnalysisIssue issue : issues) {
            for (FixCandidate candidate : issue.fixCandidates()) {
                items.add(new FixPlanItem(issue, candidate, inferEdits(issue, candidate)));
            }
        }
        return items;
    }

    private List<PatchEdit> inferEdits(AnalysisIssue issue, FixCandidate candidate) {
        if (issue.filePath() == null || issue.line() <= 0) {
            return List.of();
        }
        String sourceLine = readLine(issue);
        if (sourceLine == null || sourceLine.isBlank()) {
            return List.of();
        }
        if (issue.ruleId().equals("JAVA.STRING.EQUALITY")) {
            String replacement = rewriteStringEquality(sourceLine);
            if (replacement != null) {
                return List.of(new PatchEdit(
                        issue.filePath(),
                        issue.line(),
                        issue.line(),
                        replacement,
                        sourceLine,
                        issue.ruleId(),
                        candidate.safetyLevel() == null ? FixSafetyLevel.SAFE : candidate.safetyLevel()
                ));
            }
        }
        return List.of();
    }

    private String readLine(AnalysisIssue issue) {
        try {
            List<String> lines = Files.readAllLines(issue.filePath());
            int idx = issue.line() - 1;
            if (idx < 0 || idx >= lines.size()) {
                return null;
            }
            return lines.get(idx);
        } catch (IOException e) {
            return null;
        }
    }

    private String rewriteStringEquality(String sourceLine) {
        if (!sourceLine.contains("==") && !sourceLine.contains("!=")) {
            return null;
        }
        String operator = sourceLine.contains("==") ? "==" : "!=";
        String[] parts = sourceLine.split(operator, 2);
        if (parts.length != 2) {
            return null;
        }
        String left = sanitizeOperand(parts[0]);
        String right = sanitizeOperand(parts[1]);
        if (left.isBlank() || right.isBlank()) {
            return null;
        }
        String expr = "java.util.Objects.equals(" + left + ", " + right + ")";
        if (operator.equals("!=")) {
            expr = "!" + expr;
        }
        int eqIndex = sourceLine.indexOf(operator);
        int leftStart = Math.max(0, sourceLine.lastIndexOf('(', eqIndex));
        int rightEnd = sourceLine.indexOf(')', eqIndex);
        if (leftStart >= 0 && rightEnd > leftStart) {
            return sourceLine.substring(0, leftStart + 1) + expr + sourceLine.substring(rightEnd);
        }
        return sourceLine;
    }

    private String sanitizeOperand(String text) {
        String result = text.trim();
        result = result.replaceAll("^[!()\\s]+", "");
        result = result.replaceAll("[;,)\\s]+$", "");
        return result;
    }
}

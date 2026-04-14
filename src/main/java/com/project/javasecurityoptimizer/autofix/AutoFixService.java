package com.project.javasecurityoptimizer.autofix;

import com.github.javaparser.StaticJavaParser;
import com.project.javasecurityoptimizer.analysis.AnalyzeMode;
import com.project.javasecurityoptimizer.analysis.AnalyzeTaskRequest;
import com.project.javasecurityoptimizer.analysis.AnalyzeTaskResult;
import com.project.javasecurityoptimizer.analysis.FixSafetyLevel;
import com.project.javasecurityoptimizer.analysis.JavaAnalysisEngine;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class AutoFixService {
    private final PatchEngine patchEngine;
    private final JavaAnalysisEngine analysisEngine;
    private final Map<String, RollbackSnapshot> rollbackSnapshots = new ConcurrentHashMap<>();
    private final List<AutoFixAuditEntry> auditLog = java.util.Collections.synchronizedList(new ArrayList<>());

    public AutoFixService(PatchEngine patchEngine, JavaAnalysisEngine analysisEngine) {
        this.patchEngine = patchEngine;
        this.analysisEngine = analysisEngine;
    }

    public AutoFixResult preview(Path projectPath, List<FixPlanItem> items, FixApplyStrategy strategy) {
        String opId = operationId("preview");
        PatchPlan plan = buildFilteredPlan(items, strategy);
        List<PatchPreview> previews = buildPreviews(plan.edits());
        recordAudit(opId, "PREVIEW", strategy.operator(), true, "preview generated", touchedFiles(plan.edits()));
        return new AutoFixResult(opId, false, null, plan.conflicts(), previews, "preview-only");
    }

    public AutoFixResult applySingle(Path projectPath, FixPlanItem item, FixApplyStrategy strategy) {
        return applyBatch(projectPath, List.of(item), strategy);
    }

    public AutoFixResult applyBatch(Path projectPath, List<FixPlanItem> items, FixApplyStrategy strategy) {
        String opId = operationId("apply");
        PatchPlan plan = buildFilteredPlan(items, strategy);
        List<PatchPreview> previews = buildPreviews(plan.edits());
        if (strategy.failOnConflict() && plan.hasConflicts()) {
            recordAudit(opId, "APPLY", strategy.operator(), false, "conflict detected", touchedFiles(plan.edits()));
            return new AutoFixResult(opId, false, null, plan.conflicts(), previews, "conflict detected");
        }
        if (plan.edits().isEmpty()) {
            recordAudit(opId, "APPLY", strategy.operator(), false, "no applicable edits", List.of());
            return new AutoFixResult(opId, false, null, plan.conflicts(), previews, "no applicable edits");
        }

        String rollbackId = "rollback-" + UUID.randomUUID();
        RollbackSnapshot snapshot = captureSnapshot(rollbackId, plan.edits());
        rollbackSnapshots.put(rollbackId, snapshot);

        try {
            applyEdits(plan.edits());
            String gateMessage = runQualityGate(projectPath, plan.edits(), strategy);
            if (gateMessage != null) {
                rollbackInternal(rollbackId);
                recordAudit(opId, "APPLY", strategy.operator(), false, gateMessage, touchedFiles(plan.edits()));
                return new AutoFixResult(opId, false, rollbackId, plan.conflicts(), previews, gateMessage);
            }
            recordAudit(opId, "APPLY", strategy.operator(), true, "applied", touchedFiles(plan.edits()));
            return new AutoFixResult(opId, true, rollbackId, plan.conflicts(), previews, "ok");
        } catch (Exception e) {
            rollbackInternal(rollbackId);
            String message = "apply failed: " + e.getMessage();
            recordAudit(opId, "APPLY", strategy.operator(), false, message, touchedFiles(plan.edits()));
            return new AutoFixResult(opId, false, rollbackId, plan.conflicts(), previews, message);
        }
    }

    public boolean rollback(String rollbackId, String operator) {
        boolean success = rollbackInternal(rollbackId);
        recordAudit(operationId("rollback"), "ROLLBACK", operator, success,
                success ? "rollback completed" : "rollback snapshot not found", List.of());
        return success;
    }

    public List<AutoFixAuditEntry> latestAudits(int limit) {
        int size = auditLog.size();
        if (size == 0) {
            return List.of();
        }
        int from = Math.max(0, size - Math.max(1, limit));
        return List.copyOf(auditLog.subList(from, size));
    }

    private PatchPlan buildFilteredPlan(List<FixPlanItem> items, FixApplyStrategy strategy) {
        List<PatchEdit> accepted = new ArrayList<>();
        if (items != null) {
            for (FixPlanItem item : items) {
                for (PatchEdit edit : item.edits()) {
                    if (!strategy.allowReviewRequired() && edit.safetyLevel() == FixSafetyLevel.REVIEW_REQUIRED) {
                        continue;
                    }
                    accepted.add(edit);
                }
            }
        }
        return patchEngine.buildPlan(accepted);
    }

    private List<PatchPreview> buildPreviews(List<PatchEdit> edits) {
        Map<Path, List<String>> grouped = new LinkedHashMap<>();
        for (PatchEdit edit : edits) {
            List<String> lines = grouped.computeIfAbsent(edit.filePath(), path -> new ArrayList<>());
            lines.add("@@ -" + edit.startLine() + "," + edit.endLine() + " @@");
            if (!edit.originalSnippet().isBlank()) {
                lines.add("- " + edit.originalSnippet().replace("\n", "\\n"));
            }
            lines.add("+ " + edit.replacement().replace("\n", "\\n"));
        }
        List<PatchPreview> previews = new ArrayList<>();
        for (Map.Entry<Path, List<String>> entry : grouped.entrySet()) {
            previews.add(new PatchPreview(entry.getKey(), entry.getValue()));
        }
        return previews;
    }

    private RollbackSnapshot captureSnapshot(String rollbackId, List<PatchEdit> edits) {
        Map<Path, String> originals = new LinkedHashMap<>();
        for (Path file : edits.stream().map(PatchEdit::filePath).collect(Collectors.toCollection(java.util.LinkedHashSet::new))) {
            try {
                originals.put(file, Files.readString(file));
            } catch (IOException e) {
                throw new IllegalStateException("failed to read original file: " + file, e);
            }
        }
        return new RollbackSnapshot(rollbackId, originals, Instant.now());
    }

    private void applyEdits(List<PatchEdit> edits) {
        Map<Path, List<PatchEdit>> byFile = edits.stream()
                .collect(Collectors.groupingBy(PatchEdit::filePath, LinkedHashMap::new, Collectors.toList()));
        for (Map.Entry<Path, List<PatchEdit>> entry : byFile.entrySet()) {
            Path file = entry.getKey();
            List<PatchEdit> fileEdits = new ArrayList<>(entry.getValue());
            fileEdits.sort(Comparator.comparingInt(PatchEdit::startLine).reversed());
            List<String> lines;
            try {
                lines = new ArrayList<>(Files.readAllLines(file));
            } catch (IOException e) {
                throw new IllegalStateException("failed to read file: " + file, e);
            }
            for (PatchEdit edit : fileEdits) {
                int from = edit.startLine() - 1;
                int to = edit.endLine();
                if (from < 0 || to > lines.size() || from >= to) {
                    throw new IllegalStateException("invalid edit range for file: " + file);
                }
                List<String> replacementLines = splitReplacement(edit.replacement());
                lines.subList(from, to).clear();
                lines.addAll(from, replacementLines);
            }
            try {
                Files.writeString(file, String.join(System.lineSeparator(), lines) + System.lineSeparator());
            } catch (IOException e) {
                throw new IllegalStateException("failed to write file: " + file, e);
            }
        }
    }

    private String runQualityGate(Path projectPath, List<PatchEdit> edits, FixApplyStrategy strategy) {
        Set<Path> touched = edits.stream().map(PatchEdit::filePath).collect(Collectors.toSet());
        if (strategy.runCompileGate()) {
            for (Path file : touched) {
                try {
                    StaticJavaParser.parse(file);
                } catch (Exception e) {
                    return "quality gate failed: syntax check failed for " + file;
                }
            }
        }
        if (strategy.runRuleRecheck()) {
            Set<Path> relativeTouched = touched.stream()
                    .map(path -> projectPath.relativize(path))
                    .collect(Collectors.toSet());
            Set<String> rules = edits.stream().map(PatchEdit::ruleId).collect(Collectors.toSet());
            AnalyzeTaskRequest recheckRequest = new AnalyzeTaskRequest(
                    projectPath,
                    rules,
                    AnalyzeMode.INCREMENTAL,
                    relativeTouched,
                    Set.of(),
                    0,
                    0,
                    0,
                    Duration.ofSeconds(2),
                    0,
                    0,
                    Duration.ofMillis(500),
                    Set.of()
            );
            AnalyzeTaskResult recheckResult = analysisEngine.analyze(recheckRequest);
            if (!recheckResult.issues().isEmpty()) {
                return "quality gate failed: rule recheck still has issues=" + recheckResult.issues().size();
            }
        }
        return null;
    }

    private boolean rollbackInternal(String rollbackId) {
        RollbackSnapshot snapshot = rollbackSnapshots.remove(rollbackId);
        if (snapshot == null) {
            return false;
        }
        for (Map.Entry<Path, String> entry : snapshot.originalContents().entrySet()) {
            try {
                Files.writeString(entry.getKey(), entry.getValue());
            } catch (IOException e) {
                return false;
            }
        }
        return true;
    }

    private List<String> splitReplacement(String replacement) {
        if (replacement == null || replacement.isEmpty()) {
            return List.of();
        }
        return List.of(replacement.split("\\R", -1));
    }

    private void recordAudit(
            String operationId,
            String action,
            String operator,
            boolean success,
            String message,
            List<String> touchedFiles
    ) {
        auditLog.add(new AutoFixAuditEntry(operationId, action, operator, success, message, touchedFiles, Instant.now()));
    }

    private List<String> touchedFiles(List<PatchEdit> edits) {
        return edits.stream().map(edit -> edit.filePath().toString()).distinct().toList();
    }

    private String operationId(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    private record RollbackSnapshot(
            String rollbackId,
            Map<Path, String> originalContents,
            Instant createdAt
    ) {
    }
}

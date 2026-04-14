package com.project.javasecurityoptimizer.autofix;

import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class PatchEngine {
    public PatchPlan buildPlan(List<PatchEdit> edits) {
        if (edits == null || edits.isEmpty()) {
            return new PatchPlan(List.of(), List.of());
        }
        List<PatchEdit> normalized = new ArrayList<>(edits);
        normalized.sort(Comparator
                .comparing((PatchEdit edit) -> edit.filePath().toString())
                .thenComparingInt(PatchEdit::startLine)
                .thenComparingInt(PatchEdit::endLine));

        List<PatchConflict> conflicts = new ArrayList<>();
        List<PatchEdit> merged = new ArrayList<>();
        Map<Path, PatchEdit> lastByFile = new HashMap<>();
        for (PatchEdit edit : normalized) {
            PatchEdit previous = lastByFile.get(edit.filePath());
            if (previous != null && overlap(previous, edit)) {
                conflicts.add(new PatchConflict(
                        edit.filePath(),
                        edit.startLine(),
                        edit.endLine(),
                        "Edit range overlaps existing change"
                ));
                continue;
            }
            merged.add(edit);
            lastByFile.put(edit.filePath(), edit);
        }
        return new PatchPlan(merged, conflicts);
    }

    private boolean overlap(PatchEdit left, PatchEdit right) {
        return left.endLine() >= right.startLine() && right.endLine() >= left.startLine();
    }
}

package com.project.javasecurityoptimizer.autofix;

import com.project.javasecurityoptimizer.analysis.FixSafetyLevel;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PatchEngineTest {
    @Test
    void shouldDetectOverlappingConflicts() {
        PatchEngine engine = new PatchEngine();
        Path file = Path.of("A.java");
        PatchEdit a = new PatchEdit(file, 10, 10, "x", "old-x", "R1", FixSafetyLevel.SAFE);
        PatchEdit b = new PatchEdit(file, 10, 12, "y", "old-y", "R2", FixSafetyLevel.SAFE);

        PatchPlan plan = engine.buildPlan(List.of(a, b));

        assertEquals(1, plan.edits().size());
        assertEquals(1, plan.conflicts().size());
        assertTrue(plan.hasConflicts());
    }
}

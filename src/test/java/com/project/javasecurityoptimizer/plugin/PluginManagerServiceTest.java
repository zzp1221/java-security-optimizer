package com.project.javasecurityoptimizer.plugin;

import com.project.javasecurityoptimizer.analysis.JavaAnalysisEngine;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginManagerServiceTest {
    @Test
    void shouldBuildStartupHealthSnapshotForJavaAndCpp() {
        PluginManagerService manager = new PluginManagerService(new JavaAnalysisEngine(), "1.0.0");
        manager.startupHealthCheck();

        PluginHealthStatus javaStatus = manager.healthOf("java");
        PluginHealthStatus cppStatus = manager.healthOf("cpp");

        assertEquals(PluginRuntimeStatus.AVAILABLE, javaStatus.status());
        assertEquals(PluginRuntimeStatus.DEGRADED, cppStatus.status());
        assertTrue(manager.startupHealthSnapshot().size() >= 2);
    }

    @Test
    void shouldReturnUnavailableHintForUnknownLanguage() {
        PluginManagerService manager = new PluginManagerService(new JavaAnalysisEngine(), "1.0.0");
        String hint = manager.unavailableHint("python");

        assertTrue(hint.contains("plugin unavailable"));
        assertTrue(hint.contains("availableLanguages"));
    }
}

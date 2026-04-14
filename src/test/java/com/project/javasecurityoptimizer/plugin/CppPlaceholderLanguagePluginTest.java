package com.project.javasecurityoptimizer.plugin;

import com.project.javasecurityoptimizer.analysis.AnalyzeTaskRequest;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CppPlaceholderLanguagePluginTest {
    @Test
    void shouldExposeReservedMetaForCppPlugin() {
        CppPlaceholderLanguagePlugin plugin = new CppPlaceholderLanguagePlugin();
        PluginMeta meta = plugin.meta();

        assertEquals("cpp", meta.language());
        assertFalse(meta.implemented());
        assertFalse(meta.supportsAutofix());
    }

    @Test
    void shouldReturnNotImplementedWhenAnalyze() {
        CppPlaceholderLanguagePlugin plugin = new CppPlaceholderLanguagePlugin();

        PluginException exception = assertThrows(
                PluginException.class,
                () -> plugin.analyze(AnalyzeTaskRequest.full(Paths.get(".")))
        );
        assertEquals(PluginErrorCode.NOT_IMPLEMENTED, exception.errorCode());
    }
}

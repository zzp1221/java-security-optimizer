package com.project.javasecurityoptimizer.analysis;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ProgressEventTest {
    @Test
    void shouldClampPercentageAndKeepCurrentFile() {
        ProgressEvent event = ProgressEvent.of("parse", 120, "A.java", "解析完成");
        assertEquals(100, event.percentage());
        assertEquals("A.java", event.currentFile());
    }

    @Test
    void shouldSupportBackwardCompatibleFactory() {
        ProgressEvent event = ProgressEvent.of("index", "开始索引");
        assertEquals(0, event.percentage());
        assertNull(event.currentFile());
        assertEquals("开始索引", event.message());
    }
}

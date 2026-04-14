package com.project.javasecurityoptimizer.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class SecurityAuditServiceTest {
    @Test
    void shouldRecordFixApplyAuditEvent() {
        SecurityAuditService auditService = new SecurityAuditService();

        auditService.recordFixApply("task-001", "pack.java.security.core", "fix-12", "alice", true, "fix applied");

        assertEquals(1, auditService.recentEvents(10).size());
        SecurityAuditEvent event = auditService.recentEvents(10).get(0);
        assertEquals(SecurityAuditAction.FIX_APPLY.name(), event.userAction());
        assertEquals("task-001", event.taskId());
        assertEquals("pack.java.security.core", event.rulePackId());
        assertFalse(event.timestamp().toString().isBlank());
    }
}

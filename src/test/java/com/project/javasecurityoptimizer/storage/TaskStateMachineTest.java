package com.project.javasecurityoptimizer.storage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskStateMachineTest {
    @Test
    void shouldAllowCoreLifecycleTransitions() {
        assertTrue(TaskStateMachine.canTransit(TaskStatus.CREATED, TaskStatus.QUEUED));
        assertTrue(TaskStateMachine.canTransit(TaskStatus.QUEUED, TaskStatus.RUNNING));
        assertTrue(TaskStateMachine.canTransit(TaskStatus.RUNNING, TaskStatus.COMPLETED));
        assertTrue(TaskStateMachine.canTransit(TaskStatus.RUNNING, TaskStatus.CANCELLED));
        assertTrue(TaskStateMachine.canTransit(TaskStatus.FAILED, TaskStatus.ARCHIVED));
    }

    @Test
    void shouldRejectIllegalTransitions() {
        assertFalse(TaskStateMachine.canTransit(TaskStatus.CREATED, TaskStatus.COMPLETED));
        assertFalse(TaskStateMachine.canTransit(TaskStatus.ARCHIVED, TaskStatus.RUNNING));
        assertThrows(IllegalStateException.class, () -> TaskStateMachine.assertTransit(TaskStatus.CREATED, TaskStatus.COMPLETED));
    }
}

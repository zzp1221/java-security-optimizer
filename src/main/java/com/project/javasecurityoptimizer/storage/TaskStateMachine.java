package com.project.javasecurityoptimizer.storage;

import java.util.EnumSet;
import java.util.Map;

public final class TaskStateMachine {
    private static final Map<TaskStatus, EnumSet<TaskStatus>> TRANSITIONS = Map.of(
            TaskStatus.CREATED, EnumSet.of(TaskStatus.QUEUED, TaskStatus.CANCELLED, TaskStatus.FAILED),
            TaskStatus.QUEUED, EnumSet.of(TaskStatus.RUNNING, TaskStatus.CANCELLED, TaskStatus.FAILED),
            TaskStatus.RUNNING, EnumSet.of(TaskStatus.QUEUED, TaskStatus.COMPLETED, TaskStatus.CANCELLED, TaskStatus.FAILED),
            TaskStatus.COMPLETED, EnumSet.of(TaskStatus.ARCHIVED),
            TaskStatus.FAILED, EnumSet.of(TaskStatus.QUEUED, TaskStatus.ARCHIVED),
            TaskStatus.CANCELLED, EnumSet.of(TaskStatus.QUEUED, TaskStatus.ARCHIVED),
            TaskStatus.ARCHIVED, EnumSet.noneOf(TaskStatus.class)
    );

    private TaskStateMachine() {
    }

    public static boolean canTransit(TaskStatus from, TaskStatus to) {
        if (from == null || to == null) {
            return false;
        }
        return TRANSITIONS.getOrDefault(from, EnumSet.noneOf(TaskStatus.class)).contains(to);
    }

    public static void assertTransit(TaskStatus from, TaskStatus to) {
        if (!canTransit(from, to)) {
            throw new IllegalStateException("illegal task status transition: " + from + " -> " + to);
        }
    }
}

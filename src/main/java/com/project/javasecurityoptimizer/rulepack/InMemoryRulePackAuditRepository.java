package com.project.javasecurityoptimizer.rulepack;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class InMemoryRulePackAuditRepository implements RulePackAuditRepository {
    private final List<RulePackAuditEvent> events = new ArrayList<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    @Override
    public void append(RulePackAuditEvent event) {
        lock.writeLock().lock();
        try {
            events.add(event);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public List<RulePackAuditEvent> events() {
        lock.readLock().lock();
        try {
            return List.copyOf(events);
        } finally {
            lock.readLock().unlock();
        }
    }
}

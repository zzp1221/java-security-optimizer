package com.project.javasecurityoptimizer.rulepack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class InMemoryRulePackDistributionRepository implements RulePackDistributionRepository {
    private final Map<String, Map<String, RulePackDistributionRecord>> storage = new HashMap<>();
    private final Map<String, String> currentVersionByPack = new HashMap<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    @Override
    public void publish(RulePackDistributionRecord record) {
        lock.writeLock().lock();
        try {
            storage.computeIfAbsent(record.packId(), key -> new HashMap<>()).put(record.version(), record);
            currentVersionByPack.put(record.packId(), record.version());
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public Optional<RulePackDistributionRecord> find(String packId, String version) {
        lock.readLock().lock();
        try {
            return Optional.ofNullable(storage.getOrDefault(packId, Map.of()).get(version));
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public Optional<RulePackDistributionRecord> current(String packId) {
        lock.readLock().lock();
        try {
            String version = currentVersionByPack.get(packId);
            if (version == null) {
                return Optional.empty();
            }
            return Optional.ofNullable(storage.getOrDefault(packId, Map.of()).get(version));
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public void setCurrent(String packId, String version) {
        lock.writeLock().lock();
        try {
            if (!storage.containsKey(packId) || !storage.get(packId).containsKey(version)) {
                throw new IllegalArgumentException("rule pack version not found: " + packId + ":" + version);
            }
            currentVersionByPack.put(packId, version);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public List<RulePackDistributionRecord> history(String packId) {
        lock.readLock().lock();
        try {
            List<RulePackDistributionRecord> records = new ArrayList<>(storage.getOrDefault(packId, Map.of()).values());
            records.sort(Comparator.comparing(RulePackDistributionRecord::publishedAt).reversed());
            return List.copyOf(records);
        } finally {
            lock.readLock().unlock();
        }
    }
}

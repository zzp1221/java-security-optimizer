package com.project.javasecurityoptimizer.rulepack;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

public class InMemoryRulePackLocalRepository implements RulePackLocalRepository {
    private final List<InstalledRulePack> installed = new ArrayList<>();
    private final Set<String> activeRuleSet = new LinkedHashSet<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    @Override
    public void install(RulePackManifest manifest) {
        lock.writeLock().lock();
        try {
            installed.removeIf(item -> item.packId().equals(manifest.packId()));
            InstalledRulePack pack = new InstalledRulePack(
                    manifest.packId(),
                    manifest.version(),
                    manifest.language(),
                    manifest.checksum(),
                    Instant.now(),
                    manifest.rules().stream().map(RuleDescriptor::ruleId).collect(Collectors.toList())
            );
            installed.add(pack);
            if (activeRuleSet.isEmpty()) {
                activeRuleSet.addAll(pack.ruleIds());
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public List<InstalledRulePack> installedPacks() {
        lock.readLock().lock();
        try {
            return List.copyOf(installed);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public Set<String> activeRuleSet() {
        lock.readLock().lock();
        try {
            return Set.copyOf(activeRuleSet);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public void setActiveRuleSet(Set<String> ruleIds) {
        lock.writeLock().lock();
        try {
            activeRuleSet.clear();
            if (ruleIds != null) {
                activeRuleSet.addAll(ruleIds);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }
}

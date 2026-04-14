package com.project.javasecurityoptimizer.analysis;

public record CacheStats(
        int astCacheHits,
        int astCacheMisses,
        int symbolCacheHits,
        int symbolCacheMisses,
        int ruleCacheHits,
        int ruleCacheMisses
) {
    public static CacheStats empty() {
        return new CacheStats(0, 0, 0, 0, 0, 0);
    }

    public double astHitRate() {
        return hitRate(astCacheHits, astCacheMisses);
    }

    public double symbolHitRate() {
        return hitRate(symbolCacheHits, symbolCacheMisses);
    }

    public double ruleHitRate() {
        return hitRate(ruleCacheHits, ruleCacheMisses);
    }

    private static double hitRate(int hits, int misses) {
        int total = hits + misses;
        if (total == 0) {
            return 0D;
        }
        return (double) hits / total;
    }
}

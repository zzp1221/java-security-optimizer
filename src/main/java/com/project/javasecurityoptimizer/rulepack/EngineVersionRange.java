package com.project.javasecurityoptimizer.rulepack;

import java.util.Objects;

final class EngineVersionRange {
    private final String minVersion;
    private final boolean includeMin;
    private final String maxVersion;
    private final boolean includeMax;
    private final String exactVersion;

    private EngineVersionRange(String minVersion, boolean includeMin, String maxVersion, boolean includeMax, String exactVersion) {
        this.minVersion = minVersion;
        this.includeMin = includeMin;
        this.maxVersion = maxVersion;
        this.includeMax = includeMax;
        this.exactVersion = exactVersion;
    }

    static EngineVersionRange parse(String range) {
        Objects.requireNonNull(range, "range must not be null");
        String normalized = range.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("engineVersionRange must not be blank");
        }

        if (!normalized.contains(",")) {
            return new EngineVersionRange(null, false, null, false, normalized);
        }

        if (normalized.length() < 5) {
            throw new IllegalArgumentException("invalid engineVersionRange: " + range);
        }
        char start = normalized.charAt(0);
        char end = normalized.charAt(normalized.length() - 1);
        boolean includeMin = start == '[';
        boolean includeMax = end == ']';
        if (!(includeMin || start == '(') || !(includeMax || end == ')')) {
            throw new IllegalArgumentException("invalid engineVersionRange boundaries: " + range);
        }

        String body = normalized.substring(1, normalized.length() - 1);
        String[] parts = body.split(",", -1);
        if (parts.length != 2) {
            throw new IllegalArgumentException("invalid engineVersionRange body: " + range);
        }
        String min = parts[0].trim();
        String max = parts[1].trim();

        return new EngineVersionRange(min.isEmpty() ? null : min, includeMin, max.isEmpty() ? null : max, includeMax, null);
    }

    boolean contains(String version) {
        if (exactVersion != null) {
            return compareVersion(version, exactVersion) == 0;
        }

        if (minVersion != null) {
            int cmp = compareVersion(version, minVersion);
            if (cmp < 0 || (!includeMin && cmp == 0)) {
                return false;
            }
        }
        if (maxVersion != null) {
            int cmp = compareVersion(version, maxVersion);
            if (cmp > 0 || (!includeMax && cmp == 0)) {
                return false;
            }
        }
        return true;
    }

    private int compareVersion(String left, String right) {
        int[] leftParts = parseVersionParts(left);
        int[] rightParts = parseVersionParts(right);
        int max = Math.max(leftParts.length, rightParts.length);
        for (int i = 0; i < max; i++) {
            int lv = i < leftParts.length ? leftParts[i] : 0;
            int rv = i < rightParts.length ? rightParts[i] : 0;
            if (lv != rv) {
                return Integer.compare(lv, rv);
            }
        }
        return 0;
    }

    private int[] parseVersionParts(String version) {
        String normalized = version == null ? "" : version.trim();
        if (normalized.isEmpty()) {
            return new int[]{0};
        }
        String clean = normalized.replace("-SNAPSHOT", "");
        String[] tokens = clean.split("\\.");
        int[] values = new int[tokens.length];
        for (int i = 0; i < tokens.length; i++) {
            values[i] = parseLeadingInt(tokens[i]);
        }
        return values;
    }

    private int parseLeadingInt(String token) {
        if (token == null || token.isBlank()) {
            return 0;
        }
        StringBuilder digits = new StringBuilder();
        for (char c : token.toCharArray()) {
            if (Character.isDigit(c)) {
                digits.append(c);
            } else {
                break;
            }
        }
        if (digits.isEmpty()) {
            return 0;
        }
        return Integer.parseInt(digits.toString());
    }
}

package com.project.javasecurityoptimizer.plugin;

import com.project.javasecurityoptimizer.analysis.JavaAnalysisEngine;
import com.project.javasecurityoptimizer.rulepack.RulePackValidator;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PluginManagerService {
    private static final String DEFAULT_ENGINE_VERSION = "1.0.0";
    private static final String DEFAULT_LANGUAGE = "java";

    private final String engineVersion;
    private final Map<String, LanguagePlugin> pluginsByLanguage = new ConcurrentHashMap<>();
    private volatile List<PluginHealthStatus> startupHealthSnapshot = List.of();

    @Autowired
    public PluginManagerService(JavaAnalysisEngine javaAnalysisEngine) {
        this(javaAnalysisEngine, DEFAULT_ENGINE_VERSION);
    }

    public PluginManagerService(JavaAnalysisEngine javaAnalysisEngine, String engineVersion) {
        this.engineVersion = Objects.requireNonNull(engineVersion, "engineVersion must not be null");
        register(new JavaLanguagePlugin(javaAnalysisEngine, new RulePackValidator(), engineVersion));
        register(new CppPlaceholderLanguagePlugin());
    }

    @PostConstruct
    public void startupHealthCheck() {
        this.startupHealthSnapshot = allHealthStatus();
    }

    public String normalizeLanguage(String language) {
        if (language == null || language.isBlank()) {
            return DEFAULT_LANGUAGE;
        }
        return language.trim().toLowerCase(Locale.ROOT);
    }

    public Optional<LanguagePlugin> resolve(String language) {
        return Optional.ofNullable(pluginsByLanguage.get(normalizeLanguage(language)));
    }

    public PluginHealthStatus healthOf(String language) {
        String normalized = normalizeLanguage(language);
        LanguagePlugin plugin = pluginsByLanguage.get(normalized);
        if (plugin == null) {
            return new PluginHealthStatus(
                    normalized,
                    "N/A",
                    PluginRuntimeStatus.UNAVAILABLE,
                    false,
                    false,
                    false,
                    "language plugin not found"
            );
        }
        return buildHealthStatus(plugin);
    }

    public List<PluginHealthStatus> startupHealthSnapshot() {
        return startupHealthSnapshot;
    }

    public List<PluginHealthStatus> allHealthStatus() {
        List<PluginHealthStatus> statuses = new ArrayList<>();
        for (LanguagePlugin plugin : pluginsByLanguage.values()) {
            statuses.add(buildHealthStatus(plugin));
        }
        statuses.sort(Comparator.comparing(PluginHealthStatus::language));
        return statuses;
    }

    public Set<String> availableLanguages() {
        return pluginsByLanguage.keySet();
    }

    public String unavailableHint(String language) {
        PluginHealthStatus status = healthOf(language);
        if (status.status() == PluginRuntimeStatus.AVAILABLE) {
            return "plugin available";
        }
        List<String> available = pluginsByLanguage.values().stream()
                .map(plugin -> plugin.meta().language())
                .sorted()
                .toList();
        return "plugin unavailable for language="
                + status.language()
                + ", reason="
                + status.message()
                + ", availableLanguages="
                + available;
    }

    private void register(LanguagePlugin plugin) {
        PluginMeta meta = plugin.meta();
        pluginsByLanguage.put(meta.language().toLowerCase(Locale.ROOT), plugin);
    }

    private PluginHealthStatus buildHealthStatus(LanguagePlugin plugin) {
        PluginMeta meta = plugin.meta();
        boolean compatible = isCompatible(meta.engineVersionRange(), engineVersion);
        PluginRuntimeStatus status;
        String message;
        if (!compatible) {
            status = PluginRuntimeStatus.UNAVAILABLE;
            message = "engine version incompatible: expected "
                    + meta.engineVersionRange()
                    + ", actual "
                    + engineVersion;
        } else if (!meta.implemented()) {
            status = PluginRuntimeStatus.DEGRADED;
            message = "plugin registered as placeholder";
        } else {
            status = PluginRuntimeStatus.AVAILABLE;
            message = "ok";
        }
        return new PluginHealthStatus(
                meta.language(),
                meta.pluginId(),
                status,
                meta.implemented(),
                compatible,
                meta.supportsAutofix(),
                message
        );
    }

    private boolean isCompatible(String expectedRange, String currentVersion) {
        if (expectedRange == null || expectedRange.isBlank()) {
            return false;
        }
        String normalized = expectedRange.trim();
        if (!normalized.contains(",")) {
            return compareVersion(currentVersion, normalized) == 0;
        }
        if (normalized.length() < 5) {
            return false;
        }
        char start = normalized.charAt(0);
        char end = normalized.charAt(normalized.length() - 1);
        boolean includeMin = start == '[';
        boolean includeMax = end == ']';
        if (!(includeMin || start == '(') || !(includeMax || end == ')')) {
            return false;
        }
        String body = normalized.substring(1, normalized.length() - 1);
        String[] parts = body.split(",", -1);
        if (parts.length != 2) {
            return false;
        }
        String min = parts[0].trim();
        String max = parts[1].trim();
        if (!min.isEmpty()) {
            int cmp = compareVersion(currentVersion, min);
            if (cmp < 0 || (cmp == 0 && !includeMin)) {
                return false;
            }
        }
        if (!max.isEmpty()) {
            int cmp = compareVersion(currentVersion, max);
            if (cmp > 0 || (cmp == 0 && !includeMax)) {
                return false;
            }
        }
        return true;
    }

    private int compareVersion(String left, String right) {
        int[] lv = parseVersion(left);
        int[] rv = parseVersion(right);
        int max = Math.max(lv.length, rv.length);
        for (int i = 0; i < max; i++) {
            int l = i < lv.length ? lv[i] : 0;
            int r = i < rv.length ? rv[i] : 0;
            if (l != r) {
                return Integer.compare(l, r);
            }
        }
        return 0;
    }

    private int[] parseVersion(String input) {
        String normalized = input == null ? "" : input.trim().replace("-SNAPSHOT", "");
        if (normalized.isEmpty()) {
            return new int[]{0};
        }
        String[] tokens = normalized.split("\\.");
        int[] result = new int[tokens.length];
        for (int i = 0; i < tokens.length; i++) {
            result[i] = parseLeadingNumber(tokens[i]);
        }
        return result;
    }

    private int parseLeadingNumber(String token) {
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

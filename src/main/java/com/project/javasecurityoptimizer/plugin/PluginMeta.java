package com.project.javasecurityoptimizer.plugin;

import java.util.Objects;
import java.util.Set;

public record PluginMeta(
        String pluginId,
        String language,
        String pluginVersion,
        String engineVersionRange,
        boolean supportsAutofix,
        boolean implemented,
        Set<String> capabilities
) {
    public PluginMeta {
        Objects.requireNonNull(pluginId, "pluginId must not be null");
        Objects.requireNonNull(language, "language must not be null");
        Objects.requireNonNull(pluginVersion, "pluginVersion must not be null");
        Objects.requireNonNull(engineVersionRange, "engineVersionRange must not be null");
        capabilities = capabilities == null ? Set.of() : Set.copyOf(capabilities);
    }
}

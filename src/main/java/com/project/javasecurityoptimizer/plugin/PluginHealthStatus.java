package com.project.javasecurityoptimizer.plugin;

public record PluginHealthStatus(
        String language,
        String pluginId,
        PluginRuntimeStatus status,
        boolean implemented,
        boolean compatible,
        boolean supportsAutofix,
        String message
) {
}

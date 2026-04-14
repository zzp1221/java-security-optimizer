package com.project.javasecurityoptimizer.plugin;

import java.util.Objects;

public class PluginException extends RuntimeException {
    private final PluginErrorCode errorCode;

    public PluginException(PluginErrorCode errorCode, String message) {
        super(message);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode must not be null");
    }

    public PluginErrorCode errorCode() {
        return errorCode;
    }

    public static PluginException notImplemented(String message) {
        return new PluginException(PluginErrorCode.NOT_IMPLEMENTED, message);
    }
}

package com.project.javasecurityoptimizer.plugin;

import com.project.javasecurityoptimizer.analysis.AnalyzeTaskRequest;
import com.project.javasecurityoptimizer.analysis.AnalyzeTaskResult;
import com.project.javasecurityoptimizer.rulepack.RulePackManifest;
import com.project.javasecurityoptimizer.rulepack.RulePackSecurityContext;
import com.project.javasecurityoptimizer.rulepack.RulePackValidationResult;

import java.nio.file.Path;
import java.util.Set;

public class CppPlaceholderLanguagePlugin implements LanguagePlugin {
    private static final PluginMeta META = new PluginMeta(
            "reserved-cpp-plugin",
            "cpp",
            "0.1.0",
            "[1.0.0,2.0.0)",
            false,
            false,
            Set.of("analyze-placeholder", "validateRulePack-placeholder")
    );

    @Override
    public PluginMeta meta() {
        return META;
    }

    @Override
    public AnalyzeTaskResult analyze(AnalyzeTaskRequest request) {
        throw PluginException.notImplemented("C++ plugin is reserved and not implemented yet");
    }

    @Override
    public RulePackValidationResult validateRulePack(
            Path packageFile,
            RulePackManifest manifest,
            RulePackSecurityContext securityContext
    ) {
        throw PluginException.notImplemented("C++ rule-pack validation is not implemented yet");
    }
}

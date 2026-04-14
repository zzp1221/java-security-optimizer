package com.project.javasecurityoptimizer.plugin;

import com.project.javasecurityoptimizer.analysis.AnalyzeTaskRequest;
import com.project.javasecurityoptimizer.analysis.AnalyzeTaskResult;
import com.project.javasecurityoptimizer.analysis.JavaAnalysisEngine;
import com.project.javasecurityoptimizer.rulepack.RulePackManifest;
import com.project.javasecurityoptimizer.rulepack.RulePackSecurityContext;
import com.project.javasecurityoptimizer.rulepack.RulePackValidationResult;
import com.project.javasecurityoptimizer.rulepack.RulePackValidator;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;

public class JavaLanguagePlugin implements LanguagePlugin {
    private static final PluginMeta META = new PluginMeta(
            "builtin-java-plugin",
            "java",
            "1.0.0",
            "[1.0.0,2.0.0)",
            true,
            true,
            Set.of("analyze", "validateRulePack", "autofix")
    );

    private final JavaAnalysisEngine analysisEngine;
    private final RulePackValidator rulePackValidator;
    private final String engineVersion;

    public JavaLanguagePlugin() {
        this(new JavaAnalysisEngine(), new RulePackValidator(), "1.0.0");
    }

    public JavaLanguagePlugin(JavaAnalysisEngine analysisEngine, RulePackValidator rulePackValidator, String engineVersion) {
        this.analysisEngine = Objects.requireNonNull(analysisEngine, "analysisEngine must not be null");
        this.rulePackValidator = Objects.requireNonNull(rulePackValidator, "rulePackValidator must not be null");
        this.engineVersion = Objects.requireNonNull(engineVersion, "engineVersion must not be null");
    }

    @Override
    public PluginMeta meta() {
        return META;
    }

    @Override
    public AnalyzeTaskResult analyze(AnalyzeTaskRequest request) {
        return analysisEngine.analyze(request);
    }

    @Override
    public RulePackValidationResult validateRulePack(
            Path packageFile,
            RulePackManifest manifest,
            RulePackSecurityContext securityContext
    ) {
        return rulePackValidator.validate(packageFile, manifest, engineVersion, securityContext);
    }
}

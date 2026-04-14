package com.project.javasecurityoptimizer.plugin;

import com.project.javasecurityoptimizer.analysis.AnalyzeTaskRequest;
import com.project.javasecurityoptimizer.analysis.AnalyzeTaskResult;
import com.project.javasecurityoptimizer.rulepack.RulePackManifest;
import com.project.javasecurityoptimizer.rulepack.RulePackSecurityContext;
import com.project.javasecurityoptimizer.rulepack.RulePackValidationResult;

import java.nio.file.Path;

public interface LanguagePlugin {
    PluginMeta meta();

    AnalyzeTaskResult analyze(AnalyzeTaskRequest request);

    RulePackValidationResult validateRulePack(
            Path packageFile,
            RulePackManifest manifest,
            RulePackSecurityContext securityContext
    );
}

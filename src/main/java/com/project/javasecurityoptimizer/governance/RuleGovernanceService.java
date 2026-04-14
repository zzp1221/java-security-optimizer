package com.project.javasecurityoptimizer.governance;

import com.project.javasecurityoptimizer.analysis.RuleExecutionMetrics;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class RuleGovernanceService {
    private final ProjectRulePolicyRepository policyRepository;
    private final RuleParameterSchema parameterSchema;
    private final Map<RuleTemplateType, Set<String>> templateRuleSets;
    private final Map<String, DashboardAccumulator> dashboardByWorkspace = new ConcurrentHashMap<>();
    private final Map<String, List<FalsePositiveFeedback>> feedbackByWorkspace = new ConcurrentHashMap<>();

    public RuleGovernanceService(ProjectRulePolicyRepository policyRepository) {
        this(policyRepository, RuleParameterSchema.defaultSchema());
    }

    public RuleGovernanceService(ProjectRulePolicyRepository policyRepository, RuleParameterSchema parameterSchema) {
        this.policyRepository = Objects.requireNonNull(policyRepository, "policyRepository must not be null");
        this.parameterSchema = Objects.requireNonNull(parameterSchema, "parameterSchema must not be null");
        this.templateRuleSets = defaultTemplateRuleSets();
    }

    public Map<RuleTemplateType, Set<String>> templateRuleSets() {
        return templateRuleSets;
    }

    public RuleParameterSchema parameterSchema() {
        return parameterSchema;
    }

    public ProjectRulePolicy saveProjectPolicy(ProjectRulePolicy inputPolicy) {
        Objects.requireNonNull(inputPolicy, "inputPolicy must not be null");
        Map<String, Integer> sanitized = parameterSchema.sanitize(inputPolicy.parameters());
        ProjectRulePolicy saved = new ProjectRulePolicy(
                inputPolicy.workspaceId(),
                inputPolicy.template(),
                inputPolicy.enabledRuleIds(),
                inputPolicy.disabledRuleIds(),
                sanitized,
                inputPolicy.updatedAt()
        );
        policyRepository.save(saved);
        return saved;
    }

    public Set<String> resolveExecutableRuleSet(String workspaceId, RuleTemplateType fallbackTemplate) {
        RuleTemplateType template = fallbackTemplate == null ? RuleTemplateType.RELIABILITY : fallbackTemplate;
        ProjectRulePolicy policy = policyRepository.findByWorkspaceId(workspaceId)
                .orElseGet(() -> new ProjectRulePolicy(workspaceId, template, Set.of(), Set.of(), parameterSchema.defaultValues(), null));

        Set<String> executable = new LinkedHashSet<>(templateRuleSets.getOrDefault(policy.template(), Set.of()));
        executable.addAll(policy.enabledRuleIds());
        executable.removeAll(policy.disabledRuleIds());
        return Set.copyOf(executable);
    }

    public Map<String, Integer> resolveParameters(String workspaceId) {
        return policyRepository.findByWorkspaceId(workspaceId)
                .map(ProjectRulePolicy::parameters)
                .map(parameterSchema::sanitize)
                .orElseGet(parameterSchema::defaultValues);
    }

    public void recordAnalysisMetrics(String workspaceId, RuleExecutionMetrics metrics) {
        Objects.requireNonNull(workspaceId, "workspaceId must not be null");
        Objects.requireNonNull(metrics, "metrics must not be null");
        DashboardAccumulator accumulator = dashboardByWorkspace.computeIfAbsent(workspaceId, key -> new DashboardAccumulator());
        accumulator.append(metrics);
    }

    public void markFalsePositive(FalsePositiveFeedback feedback) {
        Objects.requireNonNull(feedback, "feedback must not be null");
        feedbackByWorkspace.computeIfAbsent(feedback.workspaceId(), key -> new ArrayList<>()).add(feedback);
        dashboardByWorkspace.computeIfAbsent(feedback.workspaceId(), key -> new DashboardAccumulator())
                .addFalsePositive(feedback.ruleId());
    }

    public RuleGovernanceDashboard dashboard(String workspaceId) {
        DashboardAccumulator accumulator = dashboardByWorkspace.get(workspaceId);
        if (accumulator == null) {
            return new RuleGovernanceDashboard(Map.of(), Map.of(), Map.of());
        }
        return accumulator.snapshot();
    }

    public List<FalsePositiveFeedback> falsePositiveFeedback(String workspaceId) {
        List<FalsePositiveFeedback> feedback = feedbackByWorkspace.get(workspaceId);
        return feedback == null ? List.of() : List.copyOf(feedback);
    }

    private Map<RuleTemplateType, Set<String>> defaultTemplateRuleSets() {
        Map<RuleTemplateType, Set<String>> templates = new EnumMap<>(RuleTemplateType.class);
        templates.put(RuleTemplateType.PERFORMANCE, Set.of(
                "JAVA.PERF.LOOP_STRING_CONCAT",
                "JAVA.CONCURRENCY.SLEEP_IN_LOOP"
        ));
        templates.put(RuleTemplateType.RELIABILITY, Set.of(
                "JAVA.NULL_DEREFERENCE",
                "JAVA.RESOURCE.NOT_CLOSED",
                "JAVA.EXCEPTION.EMPTY_CATCH",
                "JAVA.EXCEPTION.GENERIC_CATCH"
        ));
        templates.put(RuleTemplateType.MAINTAINABILITY, Set.of(
                "JAVA.STRING.EQUALITY",
                "JAVA.SECURITY.HARDCODED_CREDENTIAL"
        ));
        return Map.copyOf(templates);
    }

    private static final class DashboardAccumulator {
        private final Map<String, Long> hitCounts = new LinkedHashMap<>();
        private final Map<String, Long> durationMillis = new LinkedHashMap<>();
        private final Map<String, Long> falsePositiveCounts = new LinkedHashMap<>();

        private synchronized void append(RuleExecutionMetrics metrics) {
            for (Map.Entry<String, Integer> entry : metrics.ruleHitCounts().entrySet()) {
                hitCounts.merge(entry.getKey(), entry.getValue().longValue(), Long::sum);
            }
            for (Map.Entry<String, Long> entry : metrics.ruleDurationMillis().entrySet()) {
                durationMillis.merge(entry.getKey(), entry.getValue(), Long::sum);
            }
        }

        private synchronized void addFalsePositive(String ruleId) {
            falsePositiveCounts.merge(ruleId, 1L, Long::sum);
        }

        private synchronized RuleGovernanceDashboard snapshot() {
            return new RuleGovernanceDashboard(hitCounts, durationMillis, falsePositiveCounts);
        }
    }
}

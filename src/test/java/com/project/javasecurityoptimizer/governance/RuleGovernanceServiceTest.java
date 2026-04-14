package com.project.javasecurityoptimizer.governance;

import com.project.javasecurityoptimizer.analysis.RuleExecutionMetrics;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleGovernanceServiceTest {
    @Test
    void shouldResolveTemplatePolicyAndParametersByWorkspace() {
        RuleGovernanceService service = new RuleGovernanceService(new InMemoryProjectRulePolicyRepository());

        ProjectRulePolicy policy = new ProjectRulePolicy(
                "workspace-a",
                RuleTemplateType.PERFORMANCE,
                Set.of("JAVA.STRING.EQUALITY"),
                Set.of("JAVA.CONCURRENCY.SLEEP_IN_LOOP"),
                Map.of(
                        "complexity.threshold", 30,
                        "method.length.threshold", 1000
                ),
                Instant.now()
        );
        service.saveProjectPolicy(policy);

        Set<String> executableRules = service.resolveExecutableRuleSet("workspace-a", RuleTemplateType.RELIABILITY);
        Map<String, Integer> parameters = service.resolveParameters("workspace-a");

        assertTrue(executableRules.contains("JAVA.PERF.LOOP_STRING_CONCAT"));
        assertTrue(executableRules.contains("JAVA.STRING.EQUALITY"));
        assertTrue(!executableRules.contains("JAVA.CONCURRENCY.SLEEP_IN_LOOP"));
        assertEquals(30, parameters.get("complexity.threshold"));
        assertEquals(500, parameters.get("method.length.threshold"));
    }

    @Test
    void shouldAggregateDashboardMetricsAndFalsePositiveFeedback() {
        RuleGovernanceService service = new RuleGovernanceService(new InMemoryProjectRulePolicyRepository());
        RuleExecutionMetrics metrics = new RuleExecutionMetrics(
                Map.of(
                        "JAVA.STRING.EQUALITY", 3,
                        "JAVA.RESOURCE.NOT_CLOSED", 1
                ),
                Map.of(
                        "JAVA.STRING.EQUALITY", 12L,
                        "JAVA.RESOURCE.NOT_CLOSED", 9L
                )
        );
        service.recordAnalysisMetrics("workspace-b", metrics);
        service.markFalsePositive(new FalsePositiveFeedback(
                "workspace-b",
                "JAVA.STRING.EQUALITY",
                "fileA:12:JAVA.STRING.EQUALITY",
                "业务允许",
                Instant.now()
        ));

        RuleGovernanceDashboard dashboard = service.dashboard("workspace-b");

        assertEquals(3L, dashboard.ruleHitCounts().get("JAVA.STRING.EQUALITY"));
        assertEquals(12L, dashboard.ruleDurationMillis().get("JAVA.STRING.EQUALITY"));
        assertEquals(1L, dashboard.falsePositiveCounts().get("JAVA.STRING.EQUALITY"));
    }
}

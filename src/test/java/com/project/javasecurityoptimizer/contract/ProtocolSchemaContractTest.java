package com.project.javasecurityoptimizer.contract;

import com.project.javasecurityoptimizer.analysis.AnalysisIssue;
import com.project.javasecurityoptimizer.analysis.FixCandidate;
import com.project.javasecurityoptimizer.task.TaskSnapshot;
import com.project.javasecurityoptimizer.task.TaskSubmitRequest;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtocolSchemaContractTest {

    @Test
    void shouldKeepAnalyzeTaskSchemaStable() {
        Set<String> expected = Set.of(
                "taskId",
                "traceId",
                "workspaceId",
                "projectPath",
                "language",
                "mode",
                "ruleSet",
                "changedFiles",
                "impactedFiles",
                "maxFileSizeBytes",
                "degradeFileSizeBytes",
                "parseConcurrency",
                "parseTimeoutMillis",
                "parseRetryCount",
                "ruleConcurrency",
                "ruleTimeoutMillis",
                "degradeRuleSet",
                "priority",
                "taskTimeoutMillis",
                "maxRetries"
        );
        assertEquals(expected, recordComponents(TaskSubmitRequest.class));
    }

    @Test
    void shouldKeepIssueAndFixSchemaStable() {
        Set<String> issueSchema = Set.of("ruleId", "message", "filePath", "line", "severity", "fixCandidates");
        Set<String> fixSchema = Set.of("title", "suggestion", "safetyLevel");
        assertEquals(issueSchema, recordComponents(AnalysisIssue.class));
        assertEquals(fixSchema, recordComponents(FixCandidate.class));
    }

    @Test
    void shouldKeepTaskSnapshotSchemaStable() {
        Set<String> expected = Set.of(
                "taskId",
                "traceId",
                "workspaceId",
                "status",
                "createdAt",
                "startedAt",
                "finishedAt",
                "issueCount",
                "durationMillis",
                "failureReason",
                "failureCategory",
                "attempt",
                "maxRetries",
                "events",
                "issues"
        );
        assertEquals(expected, recordComponents(TaskSnapshot.class));
    }

    @Test
    void shouldExposeSubmitRequestAccessorMethods() {
        Set<String> accessors = Arrays.stream(TaskSubmitRequest.class.getDeclaredMethods())
                .filter(method -> method.getParameterCount() == 0)
                .map(Method::getName)
                .collect(Collectors.toSet());

        assertTrue(accessors.contains("projectPath"));
        assertTrue(accessors.contains("parseTimeoutMillis"));
        assertTrue(accessors.contains("taskTimeoutMillis"));
        assertTrue(accessors.contains("maxRetries"));
    }

    private Set<String> recordComponents(Class<?> type) {
        RecordComponent[] components = type.getRecordComponents();
        return Arrays.stream(components).map(RecordComponent::getName).collect(Collectors.toSet());
    }
}

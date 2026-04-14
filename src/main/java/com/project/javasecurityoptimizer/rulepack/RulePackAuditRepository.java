package com.project.javasecurityoptimizer.rulepack;

import java.util.List;

public interface RulePackAuditRepository {
    void append(RulePackAuditEvent event);

    List<RulePackAuditEvent> events();
}

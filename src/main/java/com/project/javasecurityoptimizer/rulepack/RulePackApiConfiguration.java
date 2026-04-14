package com.project.javasecurityoptimizer.rulepack;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.javasecurityoptimizer.security.SecurityAuditService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RulePackApiConfiguration {

    @Bean
    public RulePackLocalRepository rulePackLocalRepository() {
        return new InMemoryRulePackLocalRepository();
    }

    @Bean
    public RulePackValidator rulePackValidator() {
        return new RulePackValidator();
    }

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    @Bean
    public RulePackImportService rulePackImportService(
            RulePackValidator rulePackValidator,
            RulePackLocalRepository rulePackLocalRepository,
            SecurityAuditService securityAuditService,
            @Value("${optimizer.rulepack.engine-version:1.1.0}") String engineVersion
    ) {
        return new RulePackImportService(
                rulePackValidator,
                rulePackLocalRepository,
                engineVersion,
                securityAuditService
        );
    }
}

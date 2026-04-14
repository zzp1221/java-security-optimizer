package com.project.javasecurityoptimizer.rulepack;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class RulePackTeamSharingService {
    private final RulePackImportService importService;
    private final RulePackDistributionRepository distributionRepository;
    private final ProjectRulePackBindingRepository bindingRepository;
    private final RulePackAuditRepository auditRepository;

    public RulePackTeamSharingService(
            RulePackImportService importService,
            RulePackDistributionRepository distributionRepository,
            ProjectRulePackBindingRepository bindingRepository,
            RulePackAuditRepository auditRepository
    ) {
        this.importService = Objects.requireNonNull(importService, "importService must not be null");
        this.distributionRepository = Objects.requireNonNull(distributionRepository, "distributionRepository must not be null");
        this.bindingRepository = Objects.requireNonNull(bindingRepository, "bindingRepository must not be null");
        this.auditRepository = Objects.requireNonNull(auditRepository, "auditRepository must not be null");
    }

    public void publish(RulePackManifest manifest, String operator) {
        Objects.requireNonNull(manifest, "manifest must not be null");
        RulePackDistributionRecord record = new RulePackDistributionRecord(
                manifest.packId(),
                manifest.version(),
                manifest,
                Instant.now(),
                operator
        );
        distributionRepository.publish(record);
        auditRepository.append(new RulePackAuditEvent(
                null,
                Instant.now(),
                RulePackAuditAction.PUBLISH,
                operator,
                null,
                manifest.packId(),
                manifest.version(),
                "publish rule pack to shared channel"
        ));
    }

    public RulePackValidationResult rollback(String packId, String version, String operator) {
        try {
            distributionRepository.setCurrent(packId, version);
            auditRepository.append(new RulePackAuditEvent(
                    null,
                    Instant.now(),
                    RulePackAuditAction.ROLLBACK,
                    operator,
                    null,
                    packId,
                    version,
                    "rollback current distributed version"
            ));
            return RulePackValidationResult.ok();
        } catch (Exception e) {
            return RulePackValidationResult.fail(RulePackErrorCode.RULE_PACK_NOT_FOUND, e.getMessage());
        }
    }

    public RulePackValidationResult bindWorkspace(
            String workspaceId,
            String packId,
            String version,
            ReleaseEnvironment lockedEnvironment,
            String operator
    ) {
        Optional<RulePackDistributionRecord> record = distributionRepository.find(packId, version);
        if (record.isEmpty()) {
            return RulePackValidationResult.fail(RulePackErrorCode.RULE_PACK_NOT_FOUND, "distributed rule pack not found");
        }
        bindingRepository.bind(new ProjectRulePackBinding(
                workspaceId,
                packId,
                version,
                lockedEnvironment,
                Instant.now(),
                operator
        ));
        auditRepository.append(new RulePackAuditEvent(
                null,
                Instant.now(),
                RulePackAuditAction.BIND_WORKSPACE,
                operator,
                workspaceId,
                packId,
                version,
                "bind workspace to rule pack version and lock environment"
        ));
        return RulePackValidationResult.ok();
    }

    public RulePackValidationResult importForWorkspace(
            String workspaceId,
            Path packageFile,
            RulePackSecurityContext securityContext,
            String operator
    ) {
        Optional<ProjectRulePackBinding> binding = bindingRepository.findByWorkspaceId(workspaceId);
        if (binding.isEmpty()) {
            RulePackValidationResult result = RulePackValidationResult.fail(
                    RulePackErrorCode.RULE_PACK_NOT_BOUND,
                    "workspace has no bound rule pack"
            );
            auditRepository.append(new RulePackAuditEvent(
                    null,
                    Instant.now(),
                    RulePackAuditAction.IMPORT_REJECTED,
                    operator,
                    workspaceId,
                    null,
                    null,
                    result.message()
            ));
            return result;
        }
        ProjectRulePackBinding bound = binding.get();
        if (bound.lockedEnvironment() != securityContext.environment()) {
            RulePackValidationResult result = RulePackValidationResult.fail(
                    RulePackErrorCode.ENVIRONMENT_LOCK_MISMATCH,
                    "workspace locked environment is " + bound.lockedEnvironment()
            );
            auditRepository.append(new RulePackAuditEvent(
                    null,
                    Instant.now(),
                    RulePackAuditAction.IMPORT_REJECTED,
                    operator,
                    workspaceId,
                    bound.packId(),
                    bound.version(),
                    result.message()
            ));
            return result;
        }

        Optional<RulePackDistributionRecord> distributionRecord = distributionRepository.find(bound.packId(), bound.version());
        if (distributionRecord.isEmpty()) {
            RulePackValidationResult result = RulePackValidationResult.fail(
                    RulePackErrorCode.RULE_PACK_NOT_FOUND,
                    "bound rule pack version not found"
            );
            auditRepository.append(new RulePackAuditEvent(
                    null,
                    Instant.now(),
                    RulePackAuditAction.IMPORT_REJECTED,
                    operator,
                    workspaceId,
                    bound.packId(),
                    bound.version(),
                    result.message()
            ));
            return result;
        }

        RulePackManifest manifest = distributionRecord.get().manifest();
        RulePackValidationResult result = importService.importPack(packageFile, manifest, securityContext);
        RulePackAuditAction action = result.valid() ? RulePackAuditAction.IMPORT_ACCEPTED : RulePackAuditAction.IMPORT_REJECTED;
        auditRepository.append(new RulePackAuditEvent(
                null,
                Instant.now(),
                action,
                operator,
                workspaceId,
                manifest.packId(),
                manifest.version(),
                result.message()
        ));
        return result;
    }

    public Optional<RulePackDistributionRecord> currentDistributedVersion(String packId) {
        return distributionRepository.current(packId);
    }

    public List<RulePackAuditEvent> auditEvents() {
        return auditRepository.events();
    }
}

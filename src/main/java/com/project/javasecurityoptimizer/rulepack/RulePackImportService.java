package com.project.javasecurityoptimizer.rulepack;

import com.project.javasecurityoptimizer.security.SecurityAuditService;

import java.nio.file.Path;
import java.util.Objects;

public class RulePackImportService {
    private final RulePackValidator validator;
    private final RulePackLocalRepository localRepository;
    private final String engineVersion;
    private final SecurityAuditService securityAuditService;

    public RulePackImportService(RulePackValidator validator, RulePackLocalRepository localRepository, String engineVersion) {
        this(validator, localRepository, engineVersion, SecurityAuditService.noop());
    }

    public RulePackImportService(
            RulePackValidator validator,
            RulePackLocalRepository localRepository,
            String engineVersion,
            SecurityAuditService securityAuditService
    ) {
        this.validator = Objects.requireNonNull(validator, "validator must not be null");
        this.localRepository = Objects.requireNonNull(localRepository, "localRepository must not be null");
        this.engineVersion = Objects.requireNonNull(engineVersion, "engineVersion must not be null");
        this.securityAuditService = Objects.requireNonNull(securityAuditService, "securityAuditService must not be null");
    }

    public RulePackValidationResult importPack(Path packageFile, RulePackManifest manifest) {
        return importPack(packageFile, manifest, RulePackSecurityContext.permissive());
    }

    public RulePackValidationResult importPack(Path packageFile, RulePackManifest manifest, RulePackSecurityContext securityContext) {
        Objects.requireNonNull(packageFile, "packageFile must not be null");
        Objects.requireNonNull(manifest, "manifest must not be null");
        Objects.requireNonNull(securityContext, "securityContext must not be null");

        if (!securityContext.isPackagePathAuthorized(packageFile)) {
            securityAuditService.recordRulePackImport(manifest.packId(), false, "permission out of bounds");
            return RulePackValidationResult.fail(
                    RulePackErrorCode.PERMISSION_OUT_OF_BOUNDS,
                    "package path is out of authorized directories"
            );
        }

        RulePackValidationResult validationResult = validator.validate(packageFile, manifest, engineVersion, securityContext);
        if (!validationResult.valid()) {
            securityAuditService.recordRulePackImport(manifest.packId(), false, validationResult.message());
            return validationResult;
        }
        localRepository.install(manifest);
        securityAuditService.recordRulePackImport(manifest.packId(), true, "rule pack imported");
        return RulePackValidationResult.ok();
    }
}

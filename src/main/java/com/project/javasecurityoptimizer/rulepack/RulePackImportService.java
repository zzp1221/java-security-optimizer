package com.project.javasecurityoptimizer.rulepack;

import java.nio.file.Path;
import java.util.Objects;

public class RulePackImportService {
    private final RulePackValidator validator;
    private final RulePackLocalRepository localRepository;
    private final String engineVersion;

    public RulePackImportService(RulePackValidator validator, RulePackLocalRepository localRepository, String engineVersion) {
        this.validator = Objects.requireNonNull(validator, "validator must not be null");
        this.localRepository = Objects.requireNonNull(localRepository, "localRepository must not be null");
        this.engineVersion = Objects.requireNonNull(engineVersion, "engineVersion must not be null");
    }

    public RulePackValidationResult importPack(Path packageFile, RulePackManifest manifest) {
        return importPack(packageFile, manifest, RulePackSecurityContext.permissive());
    }

    public RulePackValidationResult importPack(Path packageFile, RulePackManifest manifest, RulePackSecurityContext securityContext) {
        Objects.requireNonNull(packageFile, "packageFile must not be null");
        Objects.requireNonNull(manifest, "manifest must not be null");
        Objects.requireNonNull(securityContext, "securityContext must not be null");

        if (!securityContext.isPackagePathAuthorized(packageFile)) {
            return RulePackValidationResult.fail(
                    RulePackErrorCode.PERMISSION_OUT_OF_BOUNDS,
                    "package path is out of authorized directories"
            );
        }

        RulePackValidationResult validationResult = validator.validate(packageFile, manifest, engineVersion, securityContext);
        if (!validationResult.valid()) {
            return validationResult;
        }
        localRepository.install(manifest);
        return RulePackValidationResult.ok();
    }
}

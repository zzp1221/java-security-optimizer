package com.project.javasecurityoptimizer.rulepack;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public record RulePackSecurityContext(
        ReleaseEnvironment environment,
        List<Path> authorizedDirectories
) {
    public RulePackSecurityContext {
        Objects.requireNonNull(environment, "environment must not be null");
        authorizedDirectories = authorizedDirectories == null
                ? List.of()
                : authorizedDirectories.stream()
                .filter(Objects::nonNull)
                .map(path -> path.toAbsolutePath().normalize())
                .toList();
    }

    public static RulePackSecurityContext permissive() {
        return new RulePackSecurityContext(ReleaseEnvironment.DEV, List.of());
    }

    public boolean isPackagePathAuthorized(Path packageFile) {
        Objects.requireNonNull(packageFile, "packageFile must not be null");
        if (authorizedDirectories.isEmpty()) {
            return true;
        }
        Path normalized = packageFile.toAbsolutePath().normalize();
        return authorizedDirectories.stream().anyMatch(normalized::startsWith);
    }
}

package com.project.javasecurityoptimizer.rulepack;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/rulepacks")
public class RulePackController {
    private final RulePackImportService importService;
    private final RulePackLocalRepository localRepository;
    private final ObjectMapper objectMapper;

    public RulePackController(
            RulePackImportService importService,
            RulePackLocalRepository localRepository,
            ObjectMapper objectMapper
    ) {
        this.importService = importService;
        this.localRepository = localRepository;
        this.objectMapper = objectMapper;
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<RulePackImportResponse> importRulePack(
            @RequestParam("packageFile") MultipartFile packageFile,
            @RequestParam(value = "manifestFile", required = false) MultipartFile manifestFile,
            @RequestParam(value = "manifestJson", required = false) String manifestJson,
            @RequestParam(value = "environment", defaultValue = "DEV") String environment
    ) {
        if (packageFile == null || packageFile.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "packageFile must not be empty");
        }
        RulePackManifest manifest = parseManifest(manifestFile, manifestJson);
        ReleaseEnvironment targetEnvironment = parseEnvironment(environment);

        Path tempPackage = null;
        try {
            String fileName = sanitizeName(packageFile.getOriginalFilename());
            String suffix = extractSuffix(fileName);
            tempPackage = Files.createTempFile("rule-pack-upload-", suffix);
            try (InputStream inputStream = packageFile.getInputStream()) {
                Files.copy(inputStream, tempPackage, StandardCopyOption.REPLACE_EXISTING);
            }
            RulePackSecurityContext securityContext = new RulePackSecurityContext(
                    targetEnvironment,
                    List.of(tempPackage.getParent())
            );
            RulePackValidationResult result = importService.importPack(tempPackage, manifest, securityContext);
            if (!result.valid()) {
                return ResponseEntity.badRequest().body(new RulePackImportResponse(
                        false,
                        manifest.packId(),
                        manifest.version(),
                        manifest.rules().size(),
                        result.message(),
                        result.errorCode() == null ? null : result.errorCode().name()
                ));
            }
            return ResponseEntity.ok(new RulePackImportResponse(
                    true,
                    manifest.packId(),
                    manifest.version(),
                    manifest.rules().size(),
                    "导入成功",
                    null
            ));
        } catch (IOException exception) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "failed to save packageFile: " + exception.getMessage(),
                    exception
            );
        } finally {
            if (tempPackage != null) {
                try {
                    Files.deleteIfExists(tempPackage);
                } catch (IOException ignored) {
                    // best effort cleanup
                }
            }
        }
    }

    @GetMapping("/installed")
    public List<InstalledRulePackView> installedRulePacks() {
        return localRepository.installedPacks().stream()
                .map(item -> new InstalledRulePackView(
                        item.packId(),
                        item.version(),
                        item.language(),
                        item.checksum(),
                        item.installedAt().toString(),
                        item.ruleIds().size()
                ))
                .toList();
    }

    private RulePackManifest parseManifest(MultipartFile manifestFile, String manifestJson) {
        String jsonPayload = manifestJson == null ? "" : manifestJson.trim();
        try {
            if (!jsonPayload.isEmpty()) {
                return objectMapper.readValue(jsonPayload, RulePackManifest.class);
            }
            if (manifestFile != null && !manifestFile.isEmpty()) {
                return objectMapper.readValue(manifestFile.getBytes(), RulePackManifest.class);
            }
        } catch (IOException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "invalid manifest json: " + exception.getMessage(),
                    exception
            );
        }
        throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "manifestJson or manifestFile is required"
        );
    }

    private ReleaseEnvironment parseEnvironment(String environment) {
        try {
            return ReleaseEnvironment.valueOf(environment.toUpperCase(Locale.ROOT));
        } catch (Exception exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "environment must be DEV or PROD"
            );
        }
    }

    private String sanitizeName(String input) {
        if (input == null || input.isBlank()) {
            return "package.bin";
        }
        String normalized = input.replace("\\", "/");
        int slash = normalized.lastIndexOf('/');
        return slash >= 0 ? normalized.substring(slash + 1) : normalized;
    }

    private String extractSuffix(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0) {
            return ".bin";
        }
        String suffix = fileName.substring(dot);
        return suffix.length() > 10 ? ".bin" : suffix;
    }

    public record RulePackImportResponse(
            boolean success,
            String packId,
            String version,
            int ruleCount,
            String message,
            String errorCode
    ) {
    }

    public record InstalledRulePackView(
            String packId,
            String version,
            String language,
            String checksum,
            String installedAt,
            int ruleCount
    ) {
    }
}

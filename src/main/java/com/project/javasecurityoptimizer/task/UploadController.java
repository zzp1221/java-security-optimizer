package com.project.javasecurityoptimizer.task;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@RestController
@RequestMapping("/uploads")
public class UploadController {
    private final Path uploadRoot;

    public UploadController() {
        this(Path.of("uploads"));
    }

    UploadController(Path uploadRoot) {
        this.uploadRoot = uploadRoot;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UploadProjectResponse uploadProjectFile(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "file must not be empty");
        }
        String originalName = sanitizeFileName(file.getOriginalFilename());
        UploadFileType fileType = detectFileType(originalName);
        if (fileType == UploadFileType.UNSUPPORTED) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "only .java, .jar and .zip files are supported"
            );
        }

        String uploadId = "upload-" + DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now()) + "-"
                + UUID.randomUUID().toString().substring(0, 8);
        Path workDir = uploadRoot.resolve(uploadId);
        Path archivePath = workDir.resolve(fileType == UploadFileType.JAR ? "source.jar" : "source.zip");
        Path extractedRoot = workDir.resolve("project");
        try {
            Files.createDirectories(extractedRoot);
            if (fileType == UploadFileType.JAVA_SOURCE) {
                Path javaFile = extractedRoot.resolve(originalName);
                try (InputStream inputStream = file.getInputStream()) {
                    Files.copy(inputStream, javaFile, StandardCopyOption.REPLACE_EXISTING);
                }
            } else {
                try (InputStream inputStream = file.getInputStream()) {
                    Files.copy(inputStream, archivePath, StandardCopyOption.REPLACE_EXISTING);
                }
                unzipSafely(archivePath, extractedRoot);
            }
        } catch (IOException ioException) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "failed to save or process upload: " + ioException.getMessage(),
                    ioException
            );
        }

        Path projectRoot = locateProjectRoot(extractedRoot);
        return new UploadProjectResponse(uploadId, projectRoot.toAbsolutePath().toString(), originalName);
    }

    private void unzipSafely(Path zipFile, Path outputDir) throws IOException {
        Path normalizedOutput = outputDir.toAbsolutePath().normalize();
        try (ZipInputStream zipInputStream = new ZipInputStream(Files.newInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                Path target = normalizedOutput.resolve(entry.getName()).normalize();
                if (!target.startsWith(normalizedOutput)) {
                    throw new IOException("illegal zip entry path: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(zipInputStream, target, StandardCopyOption.REPLACE_EXISTING);
                }
                zipInputStream.closeEntry();
            }
        }
    }

    private String sanitizeFileName(String originalName) {
        if (originalName == null || originalName.isBlank()) {
            return "";
        }
        String normalized = originalName.replace("\\", "/");
        int separatorIndex = normalized.lastIndexOf('/');
        return separatorIndex >= 0 ? normalized.substring(separatorIndex + 1) : normalized;
    }

    private UploadFileType detectFileType(String originalName) {
        if (originalName == null || originalName.isBlank()) {
            return UploadFileType.UNSUPPORTED;
        }
        String lowered = originalName.toLowerCase(Locale.ROOT);
        if (lowered.endsWith(".java")) {
            return UploadFileType.JAVA_SOURCE;
        }
        if (lowered.endsWith(".jar")) {
            return UploadFileType.JAR;
        }
        if (lowered.endsWith(".zip")) {
            return UploadFileType.ZIP;
        }
        return UploadFileType.UNSUPPORTED;
    }

    private Path locateProjectRoot(Path extractedRoot) {
        try (java.util.stream.Stream<Path> pathStream = Files.walk(extractedRoot, 3)) {
            Path pomParent = pathStream
                    .filter(path -> path.getFileName() != null && "pom.xml".equals(path.getFileName().toString()))
                    .findFirst()
                    .map(Path::getParent)
                    .orElse(extractedRoot);
            return pomParent == null ? extractedRoot : pomParent;
        } catch (IOException e) {
            return extractedRoot;
        }
    }

    public record UploadProjectResponse(
            String uploadId,
            String projectPath,
            String fileName
    ) {
    }

    private enum UploadFileType {
        JAVA_SOURCE,
        JAR,
        ZIP,
        UNSUPPORTED
    }
}

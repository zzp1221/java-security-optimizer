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
import java.util.ArrayList;
import java.util.List;
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
    public UploadProjectResponse uploadProjectFiles(@RequestParam("file") List<MultipartFile> files) {
        if (files == null || files.isEmpty() || files.stream().allMatch(file -> file == null || file.isEmpty())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "at least one file must be uploaded");
        }
        List<MultipartFile> normalizedFiles = files.stream()
                .filter(file -> file != null && !file.isEmpty())
                .toList();
        if (normalizedFiles.size() > 1) {
            for (MultipartFile file : normalizedFiles) {
                String fileName = sanitizeFileName(file.getOriginalFilename());
                if (detectFileType(fileName) != UploadFileType.JAVA_SOURCE) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "multi-file upload supports only .java files"
                    );
                }
            }
        }

        String uploadId = "upload-" + DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now()) + "-"
                + UUID.randomUUID().toString().substring(0, 8);
        Path workDir = uploadRoot.resolve(uploadId);
        Path extractedRoot = workDir.resolve("project");
        List<String> uploadedFileNames = new ArrayList<>();
        try {
            Files.createDirectories(extractedRoot);
            if (normalizedFiles.size() == 1) {
                MultipartFile file = normalizedFiles.getFirst();
                String originalName = sanitizeFileName(file.getOriginalFilename());
                UploadFileType fileType = detectFileType(originalName);
                if (fileType == UploadFileType.UNSUPPORTED) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "only .java, .jar and .zip files are supported"
                    );
                }
                uploadedFileNames.add(originalName);
                Path archivePath = workDir.resolve(fileType == UploadFileType.JAR ? "source.jar" : "source.zip");
                if (fileType == UploadFileType.JAVA_SOURCE) {
                    Path javaFile = resolveUniquePath(extractedRoot, originalName);
                    try (InputStream inputStream = file.getInputStream()) {
                        Files.copy(inputStream, javaFile, StandardCopyOption.REPLACE_EXISTING);
                    }
                } else {
                    try (InputStream inputStream = file.getInputStream()) {
                        Files.copy(inputStream, archivePath, StandardCopyOption.REPLACE_EXISTING);
                    }
                    unzipSafely(archivePath, extractedRoot);
                }
            } else {
                for (MultipartFile file : normalizedFiles) {
                    String originalName = sanitizeFileName(file.getOriginalFilename());
                    uploadedFileNames.add(originalName);
                    Path javaFile = resolveUniquePath(extractedRoot, originalName);
                    try (InputStream inputStream = file.getInputStream()) {
                        Files.copy(inputStream, javaFile, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        } catch (IOException ioException) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "failed to save or process upload: " + ioException.getMessage(),
                    ioException
            );
        }

        Path projectRoot = locateProjectRoot(extractedRoot);
        String displayName = uploadedFileNames.size() == 1
                ? uploadedFileNames.getFirst()
                : uploadedFileNames.size() + " java files";
        return new UploadProjectResponse(
                uploadId,
                projectRoot.toAbsolutePath().toString(),
                displayName,
                List.copyOf(uploadedFileNames),
                uploadedFileNames.size()
        );
    }

    UploadProjectResponse uploadProjectFile(MultipartFile file) {
        return uploadProjectFiles(List.of(file));
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

    private Path resolveUniquePath(Path parent, String fileName) {
        Path candidate = parent.resolve(fileName);
        if (!Files.exists(candidate)) {
            return candidate;
        }
        int dot = fileName.lastIndexOf('.');
        String base = dot < 0 ? fileName : fileName.substring(0, dot);
        String extension = dot < 0 ? "" : fileName.substring(dot);
        int index = 1;
        while (true) {
            Path fallback = parent.resolve(base + "-" + index + extension);
            if (!Files.exists(fallback)) {
                return fallback;
            }
            index++;
        }
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
            String fileName,
            List<String> fileNames,
            int fileCount
    ) {
        public UploadProjectResponse {
            fileNames = fileNames == null ? List.of() : List.copyOf(fileNames);
        }
    }

    private enum UploadFileType {
        JAVA_SOURCE,
        JAR,
        ZIP,
        UNSUPPORTED
    }
}

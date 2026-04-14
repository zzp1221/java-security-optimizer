package com.project.javasecurityoptimizer.task;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UploadControllerTest {

    @Test
    void shouldAcceptJavaSourceUpload() throws IOException {
        Path uploadRoot = Files.createTempDirectory("upload-controller-java");
        UploadController controller = new UploadController(uploadRoot);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "Demo.java",
                "text/x-java-source",
                "class Demo {}".getBytes(StandardCharsets.UTF_8)
        );

        UploadController.UploadProjectResponse response = controller.uploadProjectFile(file);

        Path projectPath = Path.of(response.projectPath());
        assertTrue(Files.exists(projectPath.resolve("Demo.java")));
    }

    @Test
    void shouldAcceptJarUploadAndExtractEntries() throws IOException {
        Path uploadRoot = Files.createTempDirectory("upload-controller-jar");
        UploadController controller = new UploadController(uploadRoot);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "sample.jar",
                "application/java-archive",
                buildArchive("src/Main.java", "class Main {}".getBytes(StandardCharsets.UTF_8))
        );

        UploadController.UploadProjectResponse response = controller.uploadProjectFile(file);

        Path projectPath = Path.of(response.projectPath());
        assertTrue(Files.exists(projectPath.resolve("src").resolve("Main.java")));
    }

    @Test
    void shouldRejectUnsupportedExtension() throws IOException {
        Path uploadRoot = Files.createTempDirectory("upload-controller-bad");
        UploadController controller = new UploadController(uploadRoot);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "notes.txt",
                "text/plain",
                "hello".getBytes(StandardCharsets.UTF_8)
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> controller.uploadProjectFile(file)
        );
        assertEquals(400, exception.getStatusCode().value());
    }

    private byte[] buildArchive(String entryName, byte[] content) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(output)) {
            zipOutputStream.putNextEntry(new ZipEntry(entryName));
            zipOutputStream.write(content);
            zipOutputStream.closeEntry();
        }
        return output.toByteArray();
    }
}

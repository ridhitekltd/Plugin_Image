package com.ridhitek.image.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LocalFileStorageServiceTest {

    private LocalFileStorageService service;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        service = new LocalFileStorageService();
        ReflectionTestUtils.setField(service, "baseDir", tempDir.toString());
        ReflectionTestUtils.setField(service, "appBaseUrl", "http://localhost:8181");
    }

    @Test
    void uploadFilePrependsCandidatePrefixWhenMissing() throws IOException {
        MultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", "data".getBytes());

        String result = service.uploadFile("1", "id", file);

        assertThat(result).isEqualTo("candidate_1/id/photo.jpg");
        assertThat(tempDir.resolve("candidate_1/id/photo.jpg")).exists();
    }

    @Test
    void uploadFileKeepsExistingCandidatePrefix() throws IOException {
        MultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", "data".getBytes());

        String result = service.uploadFile("candidate_1", "id", file);

        assertThat(result).isEqualTo("candidate_1/id/photo.jpg");
    }

    @Test
    void uploadFileClearsExistingFilesForL1Stage() throws IOException {
        Path stageDir = tempDir.resolve("candidate_1/l1");
        Files.createDirectories(stageDir);
        Files.write(stageDir.resolve("old.jpg"), "old".getBytes());
        Files.createDirectories(stageDir.resolve("leftover-subdir"));
        MultipartFile file = new MockMultipartFile("file", "new.jpg", "image/jpeg", "new".getBytes());

        service.uploadFile("1", "l1", file);

        assertThat(stageDir.resolve("old.jpg")).doesNotExist();
        assertThat(stageDir.resolve("leftover-subdir")).exists();
        assertThat(stageDir.resolve("new.jpg")).exists();
    }

    @Test
    void uploadFileClearsExistingFilesForVerificationStages() throws IOException {
        Path stageDir = tempDir.resolve("candidate_1/l2");
        Files.createDirectories(stageDir);
        Files.write(stageDir.resolve("old.jpg"), "old".getBytes());
        MultipartFile file = new MockMultipartFile("file", "new.jpg", "image/jpeg", "new".getBytes());

        service.uploadFile("1", "l2", file);

        assertThat(stageDir.resolve("old.jpg")).doesNotExist();
        assertThat(stageDir.resolve("new.jpg")).exists();
    }

    @Test
    void uploadFileClearsExistingFilesForL3Stage() throws IOException {
        Path stageDir = tempDir.resolve("candidate_1/l3");
        Files.createDirectories(stageDir);
        Files.write(stageDir.resolve("old.jpg"), "old".getBytes());
        MultipartFile file = new MockMultipartFile("file", "new.jpg", "image/jpeg", "new".getBytes());

        service.uploadFile("1", "l3", file);

        assertThat(stageDir.resolve("old.jpg")).doesNotExist();
        assertThat(stageDir.resolve("new.jpg")).exists();
    }

    @Test
    void uploadFileSkipsClearingWhenStageDirectoryDoesNotExistYet() throws IOException {
        MultipartFile file = new MockMultipartFile("file", "new.jpg", "image/jpeg", "new".getBytes());

        String result = service.uploadFile("1", "l1", file);

        assertThat(result).isEqualTo("candidate_1/l1/new.jpg");
        assertThat(tempDir.resolve("candidate_1/l1/new.jpg")).exists();
    }

    @Test
    void uploadFileDoesNotClearNonStageFolders() throws IOException {
        Path idDir = tempDir.resolve("candidate_1/id");
        Files.createDirectories(idDir);
        Files.write(idDir.resolve("existing.jpg"), "existing".getBytes());
        MultipartFile file = new MockMultipartFile("file", "new.jpg", "image/jpeg", "new".getBytes());

        service.uploadFile("1", "id", file);

        assertThat(idDir.resolve("existing.jpg")).exists();
        assertThat(idDir.resolve("new.jpg")).exists();
    }

    @Test
    void getFileViewUrlBuildsBackendViewPath() {
        assertThat(service.getFileViewUrl("candidate_1/id/photo.jpg"))
                .isEqualTo("http://localhost:8181/api/image/view/candidate_1/id/photo.jpg");
    }

    @Test
    void listFilesReturnsEmptyWhenDirectoryMissing() {
        assertThat(service.listFiles("missing", "id")).isEmpty();
    }

    @Test
    void listFilesFiltersToImageFilesOnly() throws IOException {
        Path dir = tempDir.resolve("candidate_1/id");
        Files.createDirectories(dir);
        Files.write(dir.resolve("photo.jpg"), "data".getBytes());
        Files.write(dir.resolve("photo.jpeg"), "data".getBytes());
        Files.write(dir.resolve("photo.png"), "data".getBytes());
        Files.write(dir.resolve("photo.webp"), "data".getBytes());
        Files.write(dir.resolve("notes.txt"), "data".getBytes());
        Files.createDirectories(dir.resolve("subdir.jpg"));

        List<String> result = service.listFiles("1", "id");

        assertThat(result).hasSize(4);
    }

    @Test
    void listFilesAcceptsAlreadyPrefixedCandidateId() throws IOException {
        Path dir = tempDir.resolve("candidate_1/id");
        Files.createDirectories(dir);
        Files.write(dir.resolve("photo.jpg"), "data".getBytes());

        List<String> result = service.listFiles("candidate_1", "id");

        assertThat(result).hasSize(1);
    }

    @Test
    void listFilesReturnsEmptyWhenPathIsNotADirectory() throws IOException {
        Path parentDir = tempDir.resolve("candidate_1");
        Files.createDirectories(parentDir);
        Path notADir = parentDir.resolve("id");
        Files.write(notADir, "data".getBytes());

        assertThat(service.listFiles("1", "id")).isEmpty();
    }
}

package com.ridhitek.image.service;

import com.google.api.gax.paging.Page;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * GcpFileStorageService eagerly resolves real GCP credentials in an instance field initializer
 * ({@code StorageOptions.getDefaultInstance().getService()}), which would fail/hang outside GCP.
 * Mockito.mock(..., CALLS_REAL_METHODS) instantiates via Objenesis, bypassing the constructor
 * (and therefore the field initializer), so we can inject a mock Storage client afterwards.
 */
class GcpFileStorageServiceTest {

    private GcpFileStorageService service;
    private Storage storage;

    @BeforeEach
    void setUp() {
        service = mock(GcpFileStorageService.class, CALLS_REAL_METHODS);
        storage = mock(Storage.class);
        ReflectionTestUtils.setField(service, "storage", storage);
        ReflectionTestUtils.setField(service, "bucketName", "test-bucket");
        ReflectionTestUtils.setField(service, "imageBaseUrl", "https://storage.googleapis.com/test-bucket");
    }

    @Test
    void uploadFileDeletesExistingBlobsForVerificationStage() throws IOException {
        Blob oldBlob = mock(Blob.class);
        BlobId oldBlobId = BlobId.of("test-bucket", "images/candidate_1/l1/old.jpg");
        when(oldBlob.getName()).thenReturn("images/candidate_1/l1/old.jpg");
        when(oldBlob.getBlobId()).thenReturn(oldBlobId);
        Page<Blob> page = mock(Page.class);
        when(page.iterateAll()).thenReturn(List.of(oldBlob));
        when(storage.list(eq("test-bucket"), any(Storage.BlobListOption.class))).thenReturn(page);

        MultipartFile file = new MockMultipartFile("file", "new.jpg", "image/jpeg", "data".getBytes());

        String result = service.uploadFile("1", "l1", file);

        verify(storage).delete(oldBlobId);
        verify(storage).create(any(BlobInfo.class), eq("data".getBytes()));
        assertThat(result).isEqualTo("images/candidate_1/l1/new.jpg");
    }

    @Test
    void uploadFileDeletesExistingBlobsForL2Stage() throws IOException {
        Page<Blob> page = mock(Page.class);
        when(page.iterateAll()).thenReturn(List.of());
        when(storage.list(eq("test-bucket"), any(Storage.BlobListOption.class))).thenReturn(page);
        MultipartFile file = new MockMultipartFile("file", "new.jpg", "image/jpeg", "data".getBytes());

        String result = service.uploadFile("1", "l2", file);

        verify(storage).list(eq("test-bucket"), any(Storage.BlobListOption.class));
        assertThat(result).isEqualTo("images/candidate_1/l2/new.jpg");
    }

    @Test
    void uploadFileDeletesExistingBlobsForL3Stage() throws IOException {
        Page<Blob> page = mock(Page.class);
        when(page.iterateAll()).thenReturn(List.of());
        when(storage.list(eq("test-bucket"), any(Storage.BlobListOption.class))).thenReturn(page);
        MultipartFile file = new MockMultipartFile("file", "new.jpg", "image/jpeg", "data".getBytes());

        String result = service.uploadFile("1", "l3", file);

        verify(storage).list(eq("test-bucket"), any(Storage.BlobListOption.class));
        assertThat(result).isEqualTo("images/candidate_1/l3/new.jpg");
    }

    @Test
    void uploadFileSkipsDeletionForNonStageFolders() throws IOException {
        MultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", "data".getBytes());

        String result = service.uploadFile("1", "id", file);

        verify(storage, never()).list(anyString(), any(Storage.BlobListOption.class));
        verify(storage).create(any(BlobInfo.class), eq("data".getBytes()));
        assertThat(result).isEqualTo("images/candidate_1/id/photo.jpg");
    }

    @Test
    void getFileViewUrlBuildsPublicGcsUrl() {
        assertThat(service.getFileViewUrl("images/candidate_1/id/photo.jpg"))
                .isEqualTo("https://storage.googleapis.com/test-bucket/images/candidate_1/id/photo.jpg");
    }

    @Test
    void listFilesFiltersToImageBlobsAndExcludesFolderPlaceholders() {
        Blob jpgBlob = mock(Blob.class);
        when(jpgBlob.getName()).thenReturn("images/candidate_1/id/photo.jpg");
        Blob jpegBlob = mock(Blob.class);
        when(jpegBlob.getName()).thenReturn("images/candidate_1/id/photo.jpeg");
        Blob pngBlob = mock(Blob.class);
        when(pngBlob.getName()).thenReturn("images/candidate_1/id/photo.png");
        Blob webpBlob = mock(Blob.class);
        when(webpBlob.getName()).thenReturn("images/candidate_1/id/photo.webp");
        Blob folderPlaceholder = mock(Blob.class);
        when(folderPlaceholder.getName()).thenReturn("images/candidate_1/id/");
        Blob nonImageBlob = mock(Blob.class);
        when(nonImageBlob.getName()).thenReturn("images/candidate_1/id/notes.txt");

        Page<Blob> page = mock(Page.class);
        when(page.iterateAll()).thenReturn(List.of(jpgBlob, jpegBlob, pngBlob, webpBlob, folderPlaceholder, nonImageBlob));
        when(storage.list(eq("test-bucket"), any(Storage.BlobListOption.class))).thenReturn(page);

        List<String> result = service.listFiles("1", "id");

        assertThat(result).hasSize(4);
    }

    @Test
    void listFilesReturnsEmptyListOnStorageException() {
        when(storage.list(eq("test-bucket"), any(Storage.BlobListOption.class)))
                .thenThrow(new RuntimeException("GCS unavailable"));

        assertThat(service.listFiles("1", "id")).isEmpty();
    }

    @Test
    void constructorResolvesStorageClientFromDefaultOptions() {
        // Covers the instance field initializer (private final Storage storage =
        // StorageOptions.getDefaultInstance().getService();) that every other test in this class
        // deliberately bypasses (see the class javadoc). Static-mocking StorageOptions lets us
        // exercise the real constructor without hitting actual GCP credentials/network.
        try (MockedStatic<StorageOptions> mockedStatic = mockStatic(StorageOptions.class)) {
            StorageOptions options = mock(StorageOptions.class);
            Storage resolvedStorage = mock(Storage.class);
            mockedStatic.when(StorageOptions::getDefaultInstance).thenReturn(options);
            when(options.getService()).thenReturn(resolvedStorage);

            GcpFileStorageService realService = new GcpFileStorageService();

            assertThat(realService).isNotNull();
            assertThat(ReflectionTestUtils.getField(realService, "storage")).isSameAs(resolvedStorage);
            mockedStatic.verify(StorageOptions::getDefaultInstance);
        }
    }
}

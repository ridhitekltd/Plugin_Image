package com.ridhitek.image.service;

import com.ridhitek.image.dto.VerificationOverrideRequestDto;
import com.ridhitek.image.dto.VerificationResultDto;
import com.ridhitek.image.entity.VerificationOverride;
import com.ridhitek.image.entity.VerificationResult;
import com.ridhitek.image.repository.VerificationOverrideRepository;
import com.ridhitek.image.repository.VerificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ImageVerificationServiceTest {

    private VerificationRepository verificationRepository;
    private VerificationOverrideRepository overrideRepository;
    private ImageVerificationService service;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        verificationRepository = mock(VerificationRepository.class);
        overrideRepository = mock(VerificationOverrideRepository.class);
        service = new ImageVerificationService();
        ReflectionTestUtils.setField(service, "verificationRepository", verificationRepository);
        ReflectionTestUtils.setField(service, "overrideRepository", overrideRepository);
        ReflectionTestUtils.setField(service, "BASE_STORAGE_PATH", tempDir.toString());
        ReflectionTestUtils.setField(service, "apiBaseUrl", "http://localhost:8181");
        when(verificationRepository.save(any(VerificationResult.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    /**
     * Forces ImageVerificationService.resolveTenantId() to return null by making both
     * TenantContextHolder test doubles report blank, runs {@code action}, then restores
     * the uniquepeople holder's default so later tests keep resolving normally.
     */
    private void withUnresolvedTenant(Runnable action) {
        com.uniquepeople.config.TenantContextHolder.tenantId = "";
        try {
            action.run();
        } finally {
            com.uniquepeople.config.TenantContextHolder.tenantId = "resolved-tenant";
        }
    }

    // --- getUnverifiedCandidates / getVerifiedCandidates ---

    @Test
    void getUnverifiedCandidatesMapsResultsAndScalesLowMatchScore() {
        VerificationResult entity = VerificationResult.builder().candidateId("1").matchScore(0.85).build();
        when(verificationRepository.findByVerificationStatus("PENDING_VERIFICATION")).thenReturn(List.of(entity));

        List<VerificationResultDto> result = service.getUnverifiedCandidates();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getMatchScore()).isEqualTo(85.0);
    }

    @Test
    void getVerifiedCandidatesMapsResultsAndKeepsHighMatchScoreUnscaled() {
        VerificationResult entity = VerificationResult.builder().candidateId("1").matchScore(92.0).build();
        when(verificationRepository.findByVerificationStatusNot("PENDING_VERIFICATION")).thenReturn(List.of(entity));

        List<VerificationResultDto> result = service.getVerifiedCandidates();

        assertThat(result.get(0).getMatchScore()).isEqualTo(92.0);
    }

    @Test
    void getUnverifiedCandidatesHandlesNullMatchScore() {
        VerificationResult entity = VerificationResult.builder().candidateId("1").matchScore(null).build();
        when(verificationRepository.findByVerificationStatus("PENDING_VERIFICATION")).thenReturn(List.of(entity));

        assertThat(service.getUnverifiedCandidates().get(0).getMatchScore()).isNull();
    }

    // --- getVerificationStatus ---

    @Test
    void getVerificationStatusReturnsMappedRecordWhenFound() {
        VerificationResult entity = VerificationResult.builder().candidateId("1").verificationStatus("VERIFIED").build();
        when(verificationRepository.findById("1")).thenReturn(Optional.of(entity));

        assertThat(service.getVerificationStatus("1").getVerificationStatus()).isEqualTo("VERIFIED");
    }

    @Test
    void getVerificationStatusReturnsNotStartedWhenMissing() {
        when(verificationRepository.findById("1")).thenReturn(Optional.empty());

        VerificationResultDto result = service.getVerificationStatus("1");

        assertThat(result.getVerificationStatus()).isEqualTo("NOT_STARTED");
        assertThat(result.getCandidateId()).isEqualTo("1");
    }

    // --- updateVerificationStatus ---

    @Test
    void updateVerificationStatusUpdatesExistingRecord() {
        VerificationResult existing = VerificationResult.builder().candidateId("1").tenantId("t1").verificationStatus("OLD").build();
        when(verificationRepository.findById("1")).thenReturn(Optional.of(existing));
        VerificationResultDto dto = VerificationResultDto.builder().verificationStatus("VERIFIED").overallConfidence(0.9).build();

        VerificationResultDto result = service.updateVerificationStatus("1", dto);

        assertThat(result.getVerificationStatus()).isEqualTo("VERIFIED");
        assertThat(result.getTenantId()).isEqualTo("t1");
    }

    @Test
    void updateVerificationStatusCreatesNewRecordAndAttemptsTenantResolution() {
        when(verificationRepository.findById("1")).thenReturn(Optional.empty());
        VerificationResultDto dto = VerificationResultDto.builder().verificationStatus("VERIFIED").build();

        VerificationResultDto result = service.updateVerificationStatus("1", dto);

        assertThat(result.getCandidateId()).isEqualTo("1");
        assertThat(result.getVerificationStatus()).isEqualTo("VERIFIED");
        // Resolved via the com.uniquepeople.config.TenantContextHolder reflection fallback
        // (test-only stand-in class); see resolveTenantId().
        assertThat(result.getTenantId()).isEqualTo("resolved-tenant");
    }

    @Test
    void updateVerificationStatusAppliesEveryProvidedField() {
        VerificationResult existing = VerificationResult.builder().candidateId("1").tenantId("keep-me").build();
        when(verificationRepository.findById("1")).thenReturn(Optional.of(existing));
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        VerificationResultDto dto = VerificationResultDto.builder()
                .verificationStatus("VERIFIED").matchScore(95.0).similarityPercentage(90.0).processedAt(now)
                .tenantId("dto-tenant")
                .l1VerificationStatus("VERIFIED").l1MatchScore(91.0).l1SimilarityPercentage(92.0).l1ProcessedAt(now)
                .l2VerificationStatus("VERIFIED").l2MatchScore(93.0).l2SimilarityPercentage(94.0).l2ProcessedAt(now)
                .l3VerificationStatus("VERIFIED").l3MatchScore(96.0).l3SimilarityPercentage(97.0).l3ProcessedAt(now)
                .build();

        VerificationResultDto result = service.updateVerificationStatus("1", dto);

        assertThat(result.getMatchScore()).isEqualTo(95.0);
        assertThat(result.getSimilarityPercentage()).isEqualTo(90.0);
        assertThat(result.getProcessedAt()).isEqualTo(now);
        assertThat(result.getTenantId()).isEqualTo("dto-tenant");
        assertThat(result.getL1MatchScore()).isEqualTo(91.0);
        assertThat(result.getL1SimilarityPercentage()).isEqualTo(92.0);
        assertThat(result.getL1ProcessedAt()).isEqualTo(now);
        assertThat(result.getL2VerificationStatus()).isEqualTo("VERIFIED");
        assertThat(result.getL2MatchScore()).isEqualTo(93.0);
        assertThat(result.getL2SimilarityPercentage()).isEqualTo(94.0);
        assertThat(result.getL2ProcessedAt()).isEqualTo(now);
        assertThat(result.getL3VerificationStatus()).isEqualTo("VERIFIED");
        assertThat(result.getL3MatchScore()).isEqualTo(96.0);
        assertThat(result.getL3SimilarityPercentage()).isEqualTo(97.0);
        assertThat(result.getL3ProcessedAt()).isEqualTo(now);
    }

    @Test
    void updateVerificationStatusResolvesTenantWhenEntityTenantIsBlankNotNull() {
        VerificationResult existing = VerificationResult.builder().candidateId("1").tenantId("").build();
        when(verificationRepository.findById("1")).thenReturn(Optional.of(existing));
        VerificationResultDto dto = VerificationResultDto.builder().verificationStatus("VERIFIED").build();

        VerificationResultDto result = service.updateVerificationStatus("1", dto);

        assertThat(result.getTenantId()).isEqualTo("resolved-tenant");
    }

    @Test
    void updateVerificationStatusLeavesTenantIdNullWhenResolutionFails() {
        withUnresolvedTenant(() -> {
            when(verificationRepository.findById("1")).thenReturn(Optional.empty());
            VerificationResultDto dto = VerificationResultDto.builder().verificationStatus("VERIFIED").build();

            VerificationResultDto result = service.updateVerificationStatus("1", dto);

            assertThat(result.getTenantId()).isNull();
        });
    }

    @Test
    void updateVerificationStatusSkipsStatusFieldWhenDtoStatusIsNull() {
        VerificationResult existing = VerificationResult.builder().candidateId("1").tenantId("t1").verificationStatus("OLD").build();
        when(verificationRepository.findById("1")).thenReturn(Optional.of(existing));
        // verificationStatus intentionally left null; only another field is set.
        VerificationResultDto dto = VerificationResultDto.builder().matchScore(77.0).build();

        VerificationResultDto result = service.updateVerificationStatus("1", dto);

        assertThat(result.getVerificationStatus()).isEqualTo("OLD");
        assertThat(result.getMatchScore()).isEqualTo(77.0);
    }

    // --- resolveTenantId (reflective TenantContextHolder lookups) ---

    @Test
    void resolveTenantIdUsesRidhitekBackendHolderWhenNonBlank() {
        com.ridhitek.backend.config.TenantContextHolder.tenantId = "ridhitek-tenant";
        try {
            when(verificationRepository.findById("1")).thenReturn(Optional.empty());
            VerificationResultDto dto = VerificationResultDto.builder().verificationStatus("VERIFIED").build();

            VerificationResultDto result = service.updateVerificationStatus("1", dto);

            assertThat(result.getTenantId()).isEqualTo("ridhitek-tenant");
        } finally {
            com.ridhitek.backend.config.TenantContextHolder.tenantId = "";
        }
    }

    @Test
    void resolveTenantIdFallsBackToUniquepeopleHolderWhenRidhitekHolderIsNull() {
        com.ridhitek.backend.config.TenantContextHolder.tenantId = null;
        try {
            when(verificationRepository.findById("1")).thenReturn(Optional.empty());
            VerificationResultDto dto = VerificationResultDto.builder().verificationStatus("VERIFIED").build();

            VerificationResultDto result = service.updateVerificationStatus("1", dto);

            assertThat(result.getTenantId()).isEqualTo("resolved-tenant");
        } finally {
            com.ridhitek.backend.config.TenantContextHolder.tenantId = "";
        }
    }

    @Test
    void resolveTenantIdFallsBackToUniquepeopleHolderWhenRidhitekHolderThrows() {
        com.ridhitek.backend.config.TenantContextHolder.throwOnAccess = true;
        try {
            when(verificationRepository.findById("1")).thenReturn(Optional.empty());
            VerificationResultDto dto = VerificationResultDto.builder().verificationStatus("VERIFIED").build();

            VerificationResultDto result = service.updateVerificationStatus("1", dto);

            assertThat(result.getTenantId()).isEqualTo("resolved-tenant");
        } finally {
            com.ridhitek.backend.config.TenantContextHolder.throwOnAccess = false;
        }
    }

    @Test
    void resolveTenantIdReturnsNullWhenUniquepeopleHolderIsNull() {
        com.uniquepeople.config.TenantContextHolder.tenantId = null;
        try {
            when(verificationRepository.findById("1")).thenReturn(Optional.empty());
            VerificationResultDto dto = VerificationResultDto.builder().verificationStatus("VERIFIED").build();

            VerificationResultDto result = service.updateVerificationStatus("1", dto);

            assertThat(result.getTenantId()).isNull();
        } finally {
            com.uniquepeople.config.TenantContextHolder.tenantId = "resolved-tenant";
        }
    }

    @Test
    void resolveTenantIdReturnsNullWhenUniquepeopleHolderThrows() {
        com.uniquepeople.config.TenantContextHolder.throwOnAccess = true;
        try {
            when(verificationRepository.findById("1")).thenReturn(Optional.empty());
            VerificationResultDto dto = VerificationResultDto.builder().verificationStatus("VERIFIED").build();

            VerificationResultDto result = service.updateVerificationStatus("1", dto);

            assertThat(result.getTenantId()).isNull();
        } finally {
            com.uniquepeople.config.TenantContextHolder.throwOnAccess = false;
        }
    }

    // --- bulkUpdateVerificationStatus ---

    @Test
    void bulkUpdateVerificationStatusUpdatesEachEntry() {
        when(verificationRepository.findById(anyString())).thenReturn(Optional.empty());
        VerificationResultDto dto1 = VerificationResultDto.builder().candidateId("1").verificationStatus("VERIFIED").build();
        VerificationResultDto dto2 = VerificationResultDto.builder().candidateId("2").verificationStatus("REJECTED").build();

        List<VerificationResultDto> result = service.bulkUpdateVerificationStatus(List.of(dto1, dto2));

        assertThat(result).extracting(VerificationResultDto::getVerificationStatus)
                .containsExactly("VERIFIED", "REJECTED");
    }

    // --- getVerificationStatusCounts ---

    @Test
    void getVerificationStatusCountsDelegatesToRepository() {
        List<Object[]> counts = java.util.Collections.singletonList(new Object[]{"VERIFIED", 5L});
        when(verificationRepository.getVerificationStatusCounts()).thenReturn(counts);

        assertThat(service.getVerificationStatusCounts()).isSameAs(counts);
    }

    // --- uploadPhotos (bulk) ---

    @Test
    void uploadPhotosLocalStorageHandlesAllCategories() {
        when(verificationRepository.findById("1")).thenReturn(Optional.empty());
        MultipartFile id = new MockMultipartFile("file", "id.jpg", "image/jpeg", "id-data".getBytes());
        MultipartFile shot = new MockMultipartFile("file", "shot.jpg", "image/jpeg", "shot-data".getBytes());
        MultipartFile l1 = new MockMultipartFile("file", "l1.jpg", "image/jpeg", "l1-data".getBytes());
        MultipartFile l2 = new MockMultipartFile("file", "l2.jpg", "image/jpeg", "l2-data".getBytes());
        MultipartFile l3 = new MockMultipartFile("file", "l3.jpg", "image/jpeg", "l3-data".getBytes());

        Map<String, Object> response = service.uploadPhotos("1", "stage", "PAN",
                new MultipartFile[]{id}, new MultipartFile[]{shot},
                new MultipartFile[]{l1}, new MultipartFile[]{l2}, new MultipartFile[]{l3});

        @SuppressWarnings("unchecked")
        List<String> uploaded = (List<String>) response.get("uploadedFiles");
        assertThat(uploaded).anyMatch(s -> s.startsWith("ID:"));
        assertThat(uploaded).anyMatch(s -> s.startsWith("Candidate:"));

        ArgumentCaptor<VerificationResult> captor = ArgumentCaptor.forClass(VerificationResult.class);
        verify(verificationRepository).save(captor.capture());
        VerificationResult saved = captor.getValue();
        assertThat(saved.getVerificationStatus()).isEqualTo("PENDING_VERIFICATION");
        assertThat(saved.getL1VerificationStatus()).isEqualTo("PENDING_VERIFICATION");
        assertThat(saved.getL2VerificationStatus()).isEqualTo("PENDING_VERIFICATION");
        assertThat(saved.getL3VerificationStatus()).isEqualTo("PENDING_VERIFICATION");
        assertThat(tempDir.resolve("candidate_1/id/id.jpg")).exists();
        assertThat(tempDir.resolve("candidate_1/l1/l1.jpg")).exists();
    }

    @Test
    void uploadPhotosWithAllNullArraysOnlySetsOverallStatus() {
        when(verificationRepository.findById("1")).thenReturn(Optional.empty());

        service.uploadPhotos("1", "stage", "PAN", null, null, null, null, null);

        ArgumentCaptor<VerificationResult> captor = ArgumentCaptor.forClass(VerificationResult.class);
        verify(verificationRepository).save(captor.capture());
        VerificationResult saved = captor.getValue();
        assertThat(saved.getVerificationStatus()).isEqualTo("PENDING_VERIFICATION");
        assertThat(saved.getL1VerificationStatus()).isNull();
        assertThat(saved.getL2VerificationStatus()).isNull();
        assertThat(saved.getL3VerificationStatus()).isNull();
    }

    @Test
    void uploadPhotosDelegatesToStorageServiceWhenConfigured() throws IOException {
        StorageService storageService = mock(StorageService.class);
        ReflectionTestUtils.setField(service, "storageService", storageService);
        when(storageService.uploadFile(anyString(), anyString(), any())).thenReturn("gcs/path");
        when(verificationRepository.findById("1")).thenReturn(Optional.empty());
        MultipartFile id = new MockMultipartFile("file", "id.jpg", "image/jpeg", "data".getBytes());

        service.uploadPhotos("1", "stage", "PAN", new MultipartFile[]{id}, null, null, null, null);

        verify(storageService).uploadFile("1", "id", id);
        assertThat(tempDir.resolve("candidate_1")).doesNotExist();
    }

    @Test
    void uploadPhotosKeepsExistingTenantIdWithoutResolving() {
        VerificationResult existing = VerificationResult.builder().candidateId("1").tenantId("existing-tenant").build();
        when(verificationRepository.findById("1")).thenReturn(Optional.of(existing));

        service.uploadPhotos("1", "stage", "PAN", null, null, null, null, null);

        ArgumentCaptor<VerificationResult> captor = ArgumentCaptor.forClass(VerificationResult.class);
        verify(verificationRepository).save(captor.capture());
        assertThat(captor.getValue().getTenantId()).isEqualTo("existing-tenant");
    }

    @Test
    void uploadPhotosResolvesTenantWhenExistingTenantBlank() {
        VerificationResult existing = VerificationResult.builder().candidateId("1").tenantId("").build();
        when(verificationRepository.findById("1")).thenReturn(Optional.of(existing));

        service.uploadPhotos("1", "stage", "PAN", null, null, null, null, null);

        ArgumentCaptor<VerificationResult> captor = ArgumentCaptor.forClass(VerificationResult.class);
        verify(verificationRepository).save(captor.capture());
        assertThat(captor.getValue().getTenantId()).isEqualTo("resolved-tenant");
    }

    @Test
    void uploadPhotosLeavesTenantIdNullWhenResolutionFails() {
        withUnresolvedTenant(() -> {
            when(verificationRepository.findById("1")).thenReturn(Optional.empty());

            service.uploadPhotos("1", "stage", "PAN", null, null, null, null, null);

            ArgumentCaptor<VerificationResult> captor = ArgumentCaptor.forClass(VerificationResult.class);
            verify(verificationRepository).save(captor.capture());
            assertThat(captor.getValue().getTenantId()).isNull();
        });
    }

    // --- uploadPhoto (single) ---

    @Test
    void uploadPhotoResolvesIdSubfolder() {
        when(verificationRepository.findById("1")).thenReturn(Optional.empty());
        MultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", "data".getBytes());

        service.uploadPhoto("1", "ID_CARD", file);

        assertThat(tempDir.resolve("candidate_1/id/photo.jpg")).exists();
    }

    @Test
    void uploadPhotoResolvesCandidateSubfolderForPhotoStage() {
        // Note: "candidate" contains the substring "id" (canD-ID-ate), so the controller's
        // id-check always wins for that word; only a stage without "id" in it (e.g. "photo")
        // reaches the candidate/photo branch.
        when(verificationRepository.findById("1")).thenReturn(Optional.empty());
        MultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", "data".getBytes());

        VerificationResultDto result = service.uploadPhoto("1", "photo", file);

        assertThat(result.getVerificationStatus()).isEqualTo("PENDING_VERIFICATION");
        assertThat(tempDir.resolve("candidate_1/candidate/photo.jpg")).exists();
    }

    @Test
    void uploadPhotoSetsL1StatusForL1Stage() {
        when(verificationRepository.findById("1")).thenReturn(Optional.empty());
        MultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", "data".getBytes());

        VerificationResultDto result = service.uploadPhoto("1", "l1", file);

        assertThat(result.getL1VerificationStatus()).isEqualTo("PENDING_VERIFICATION");
    }

    @Test
    void uploadPhotoSetsL2StatusForL2Stage() {
        when(verificationRepository.findById("1")).thenReturn(Optional.empty());
        MultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", "data".getBytes());

        VerificationResultDto result = service.uploadPhoto("1", "l2", file);

        assertThat(result.getL2VerificationStatus()).isEqualTo("PENDING_VERIFICATION");
    }

    @Test
    void uploadPhotoSetsL3StatusForL3Stage() {
        when(verificationRepository.findById("1")).thenReturn(Optional.empty());
        MultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", "data".getBytes());

        VerificationResultDto result = service.uploadPhoto("1", "l3", file);

        assertThat(result.getL3VerificationStatus()).isEqualTo("PENDING_VERIFICATION");
    }

    @Test
    void uploadPhotoLeavesSubfolderUnchangedForUnmatchedStage() {
        when(verificationRepository.findById("1")).thenReturn(Optional.empty());
        MultipartFile file = new MockMultipartFile("file", "misc.jpg", "image/jpeg", "data".getBytes());

        service.uploadPhoto("1", "other", file);

        assertThat(tempDir.resolve("candidate_1/other/misc.jpg")).exists();
    }

    @Test
    void uploadPhotoKeepsExistingTenantIdWithoutResolving() {
        VerificationResult existing = VerificationResult.builder().candidateId("1").tenantId("existing-tenant").build();
        when(verificationRepository.findById("1")).thenReturn(Optional.of(existing));
        MultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", "data".getBytes());

        VerificationResultDto result = service.uploadPhoto("1", "id", file);

        assertThat(result.getTenantId()).isEqualTo("existing-tenant");
    }

    @Test
    void uploadPhotoResolvesTenantWhenExistingTenantBlank() {
        VerificationResult existing = VerificationResult.builder().candidateId("1").tenantId("").build();
        when(verificationRepository.findById("1")).thenReturn(Optional.of(existing));
        MultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", "data".getBytes());

        VerificationResultDto result = service.uploadPhoto("1", "id", file);

        assertThat(result.getTenantId()).isEqualTo("resolved-tenant");
    }

    @Test
    void uploadPhotoLeavesTenantIdNullWhenResolutionFails() {
        withUnresolvedTenant(() -> {
            when(verificationRepository.findById("1")).thenReturn(Optional.empty());
            MultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", "data".getBytes());

            VerificationResultDto result = service.uploadPhoto("1", "id", file);

            assertThat(result.getTenantId()).isNull();
        });
    }

    // --- uploadSingleFile ---

    @Test
    void uploadSingleFileReturnsNullForNullFile() {
        assertThat(service.uploadSingleFile("1", "id", null)).isNull();
    }

    @Test
    void uploadSingleFileReturnsNullForEmptyFile() {
        MultipartFile empty = new MockMultipartFile("file", "empty.jpg", "image/jpeg", new byte[0]);
        assertThat(service.uploadSingleFile("1", "id", empty)).isNull();
    }

    @Test
    void uploadSingleFileClearsExistingFilesForStageFolders() throws IOException {
        Path stageDir = tempDir.resolve("candidate_1/l1");
        Files.createDirectories(stageDir);
        Files.write(stageDir.resolve("old.jpg"), "old".getBytes());
        Files.createDirectories(stageDir.resolve("leftover-subdir"));
        MultipartFile file = new MockMultipartFile("file", "new.jpg", "image/jpeg", "new".getBytes());

        service.uploadSingleFile("1", "l1", file);

        assertThat(stageDir.resolve("old.jpg")).doesNotExist();
        assertThat(stageDir.resolve("leftover-subdir")).exists();
        assertThat(stageDir.resolve("new.jpg")).exists();
    }

    @Test
    void uploadSingleFileCreatesStageDirectoryWhenAbsent() throws IOException {
        MultipartFile file = new MockMultipartFile("file", "new.jpg", "image/jpeg", "new".getBytes());

        service.uploadSingleFile("1", "l2", file);

        assertThat(tempDir.resolve("candidate_1/l2/new.jpg")).exists();
    }

    @Test
    void uploadSingleFileClearsL3StageFolderToo() throws IOException {
        Path stageDir = tempDir.resolve("candidate_1/l3");
        Files.createDirectories(stageDir);
        Files.write(stageDir.resolve("old.jpg"), "old".getBytes());
        MultipartFile file = new MockMultipartFile("file", "new.jpg", "image/jpeg", "new".getBytes());

        service.uploadSingleFile("1", "l3", file);

        assertThat(stageDir.resolve("old.jpg")).doesNotExist();
        assertThat(stageDir.resolve("new.jpg")).exists();
    }

    @Test
    void uploadSingleFileDoesNotClearNonStageFolders() throws IOException {
        Path idDir = tempDir.resolve("candidate_1/id");
        Files.createDirectories(idDir);
        Files.write(idDir.resolve("existing.jpg"), "existing".getBytes());
        MultipartFile file = new MockMultipartFile("file", "new.jpg", "image/jpeg", "new".getBytes());

        service.uploadSingleFile("1", "id", file);

        assertThat(idDir.resolve("existing.jpg")).exists();
        assertThat(idDir.resolve("new.jpg")).exists();
    }

    @Test
    void uploadSingleFileHandlesNullListFilesWhenStagePathIsActuallyAFile() throws IOException {
        // Pre-create the l1 stage path as a plain file (not a directory). dir.exists() is true
        // but dir.listFiles() returns null since it isn't a directory, covering the false branch
        // of "if (existingFiles != null)" in the replacement-clearing logic. The subsequent
        // Files.write() then fails because the parent path isn't a directory, which the method
        // wraps into a RuntimeException like any other IOException.
        Path stagePath = tempDir.resolve("candidate_1/l1");
        Files.createDirectories(stagePath.getParent());
        Files.write(stagePath, "not a directory".getBytes());
        MultipartFile file = new MockMultipartFile("file", "new.jpg", "image/jpeg", "new".getBytes());

        assertThatThrownBy(() -> service.uploadSingleFile("1", "l1", file))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("File upload failed");
    }

    @Test
    void uploadSingleFileWrapsIOExceptionAsRuntimeException() throws IOException {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn("boom.jpg");
        when(file.getBytes()).thenThrow(new IOException("disk full"));

        assertThatThrownBy(() -> service.uploadSingleFile("1", "id", file))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("File upload failed");
    }

    @Test
    void uploadSingleFileDelegatesToStorageServiceWhenConfigured() throws IOException {
        StorageService storageService = mock(StorageService.class);
        ReflectionTestUtils.setField(service, "storageService", storageService);
        MultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", "data".getBytes());
        when(storageService.uploadFile("1", "id", file)).thenReturn("gcs/path/photo.jpg");

        String result = service.uploadSingleFile("1", "id", file);

        assertThat(result).isEqualTo("gcs/path/photo.jpg");
        assertThat(tempDir.resolve("candidate_1")).doesNotExist();
    }

    // --- getCandidatePhotos ---

    @Test
    void getCandidatePhotosListsOnlyImageFilesLocally() throws IOException {
        Path idDir = tempDir.resolve("candidate_1/id");
        Files.createDirectories(idDir);
        Files.write(idDir.resolve("photo.jpg"), "data".getBytes());
        Files.write(idDir.resolve("photo.jpeg"), "data".getBytes());
        Files.write(idDir.resolve("photo.png"), "data".getBytes());
        Files.write(idDir.resolve("photo.webp"), "data".getBytes());
        Files.write(idDir.resolve("notes.txt"), "data".getBytes());
        Files.createDirectories(idDir.resolve("subdir.jpg"));

        Map<String, Object> response = service.getCandidatePhotos("1");

        @SuppressWarnings("unchecked")
        List<String> idPhotos = (List<String>) response.get("idPhotos");
        assertThat(idPhotos).hasSize(4);
        assertThat(idPhotos).allMatch(url -> url.startsWith("http://localhost:8181"));
    }

    @Test
    void getCandidatePhotosReturnsEmptyListsWhenDirectoryMissing() {
        Map<String, Object> response = service.getCandidatePhotos("missing-candidate");

        @SuppressWarnings("unchecked")
        List<String> idPhotos = (List<String>) response.get("idPhotos");
        assertThat(idPhotos).isEmpty();
    }

    @Test
    void getCandidatePhotosReturnsEmptyListsWhenPathIsNotADirectory() throws IOException {
        Path parentDir = tempDir.resolve("candidate_1");
        Files.createDirectories(parentDir);
        Files.write(parentDir.resolve("id"), "data".getBytes());

        Map<String, Object> response = service.getCandidatePhotos("1");

        @SuppressWarnings("unchecked")
        List<String> idPhotos = (List<String>) response.get("idPhotos");
        assertThat(idPhotos).isEmpty();
    }

    @Test
    void getCandidatePhotosDelegatesToStorageServiceWhenConfigured() {
        StorageService storageService = mock(StorageService.class);
        ReflectionTestUtils.setField(service, "storageService", storageService);
        when(storageService.listFiles("1", "id")).thenReturn(List.of("gcs/url1"));

        Map<String, Object> response = service.getCandidatePhotos("1");

        assertThat(response.get("idPhotos")).isEqualTo(List.of("gcs/url1"));
    }

    @Test
    void getCandidatePhotosReturnsEmptyListWhenStorageServiceThrows() {
        StorageService storageService = mock(StorageService.class);
        ReflectionTestUtils.setField(service, "storageService", storageService);
        when(storageService.listFiles(anyString(), anyString())).thenThrow(new RuntimeException("boom"));

        Map<String, Object> response = service.getCandidatePhotos("1");

        assertThat(response.get("idPhotos")).isEqualTo(List.of());
    }

    // --- getVerificationHistory ---

    @Test
    void getVerificationHistoryMapsOverrides() {
        VerificationOverride override = new VerificationOverride();
        override.setCandidateId("1");
        override.setOldStatus("PENDING");
        override.setNewStatus("VERIFIED");
        when(overrideRepository.findByCandidateIdOrderByOverriddenAtDesc("1")).thenReturn(List.of(override));

        List<?> history = service.getVerificationHistory("1");

        assertThat(history).hasSize(1);
    }

    // --- overrideVerificationStatus ---

    @Test
    void overrideVerificationStatusThrowsWhenCandidateNotFound() {
        when(verificationRepository.findById("1")).thenReturn(Optional.empty());
        VerificationOverrideRequestDto request = new VerificationOverrideRequestDto();
        request.setCandidateId("1");

        assertThatThrownBy(() -> service.overrideVerificationStatus(request, "admin"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Candidate verification not found");
    }

    @Test
    void overrideVerificationStatusWithNullStageOnlySetsOverallStatus() {
        VerificationResult existing = VerificationResult.builder().candidateId("1").verificationStatus("PENDING_VERIFICATION").build();
        when(verificationRepository.findById("1")).thenReturn(Optional.of(existing));
        VerificationOverrideRequestDto request = new VerificationOverrideRequestDto();
        request.setCandidateId("1");
        request.setNewStatus("VERIFIED");

        VerificationResultDto result = service.overrideVerificationStatus(request, "admin");

        assertThat(result.getVerificationStatus()).isEqualTo("VERIFIED");
        assertThat(result.getL1VerificationStatus()).isNull();
        verify(overrideRepository).save(any(VerificationOverride.class));
    }

    @Test
    void overrideVerificationStatusL1VsL2SetsBothLevels() {
        assertStageSetsLevels("L1_VS_L2", true, true, false);
    }

    @Test
    void overrideVerificationStatusL2VsL3SetsOnlyL3() {
        assertStageSetsLevels("L2_VS_L3", false, false, true);
    }

    @Test
    void overrideVerificationStatusL1VsL3SetsL1AndL3() {
        assertStageSetsLevels("L1_VS_L3", true, false, true);
    }

    @Test
    void overrideVerificationStatusAllSetsAllLevels() {
        assertStageSetsLevels("ALL", true, true, true);
    }

    @Test
    void overrideVerificationStatusUnrecognizedStageSetsNoLevels() {
        assertStageSetsLevels("SOMETHING_ELSE", false, false, false);
    }

    @Test
    void overrideVerificationStatusAllAliasSetsAllLevels() {
        assertStageSetsLevels("L1_VS_L2_AND_L2_VS_L3", true, true, true);
    }

    private void assertStageSetsLevels(String stage, boolean l1, boolean l2, boolean l3) {
        VerificationResult existing = VerificationResult.builder().candidateId("1").build();
        when(verificationRepository.findById("1")).thenReturn(Optional.of(existing));
        VerificationOverrideRequestDto request = new VerificationOverrideRequestDto();
        request.setCandidateId("1");
        request.setNewStatus("VERIFIED");
        request.setStage(stage);

        VerificationResultDto result = service.overrideVerificationStatus(request, "admin");

        assertThat(result.getL1VerificationStatus()).isEqualTo(l1 ? "VERIFIED" : null);
        assertThat(result.getL2VerificationStatus()).isEqualTo(l2 ? "VERIFIED" : null);
        assertThat(result.getL3VerificationStatus()).isEqualTo(l3 ? "VERIFIED" : null);
    }

    @Test
    void overrideVerificationStatusUsesTenantFromRequestWhenPresent() {
        VerificationResult existing = VerificationResult.builder().candidateId("1").tenantId("entity-tenant").build();
        when(verificationRepository.findById("1")).thenReturn(Optional.of(existing));
        VerificationOverrideRequestDto request = new VerificationOverrideRequestDto();
        request.setCandidateId("1");
        request.setNewStatus("VERIFIED");
        request.setTenantId("request-tenant");

        service.overrideVerificationStatus(request, "admin");

        ArgumentCaptor<VerificationOverride> captor = ArgumentCaptor.forClass(VerificationOverride.class);
        verify(overrideRepository).save(captor.capture());
        assertThat(captor.getValue().getTenantId()).isEqualTo("request-tenant");
    }

    @Test
    void overrideVerificationStatusFallsBackToEntityTenantWhenRequestBlank() {
        VerificationResult existing = VerificationResult.builder().candidateId("1").tenantId("entity-tenant").build();
        when(verificationRepository.findById("1")).thenReturn(Optional.of(existing));
        VerificationOverrideRequestDto request = new VerificationOverrideRequestDto();
        request.setCandidateId("1");
        request.setNewStatus("VERIFIED");

        service.overrideVerificationStatus(request, "admin");

        ArgumentCaptor<VerificationOverride> captor = ArgumentCaptor.forClass(VerificationOverride.class);
        verify(overrideRepository).save(captor.capture());
        assertThat(captor.getValue().getTenantId()).isEqualTo("entity-tenant");
    }

    @Test
    void overrideVerificationStatusTreatsBlankRequestTenantAsAbsent() {
        VerificationResult existing = VerificationResult.builder().candidateId("1").tenantId("entity-tenant").build();
        when(verificationRepository.findById("1")).thenReturn(Optional.of(existing));
        VerificationOverrideRequestDto request = new VerificationOverrideRequestDto();
        request.setCandidateId("1");
        request.setNewStatus("VERIFIED");
        request.setTenantId("");

        service.overrideVerificationStatus(request, "admin");

        ArgumentCaptor<VerificationOverride> captor = ArgumentCaptor.forClass(VerificationOverride.class);
        verify(overrideRepository).save(captor.capture());
        assertThat(captor.getValue().getTenantId()).isEqualTo("entity-tenant");
    }

    @Test
    void overrideVerificationStatusReResolvesWhenEntityTenantFallbackIsBlankString() {
        // request.getTenantId() is null (absent) so resolvedTenantId falls back to
        // result.getTenantId(), which here is "" (non-null but blank) rather than null. That
        // exercises the (resolvedTenantId == null || resolvedTenantId.trim().isEmpty()) guard's
        // "non-null but blank" branch, which then triggers the resolveTenantId() fallback.
        VerificationResult existing = VerificationResult.builder().candidateId("1").tenantId("").build();
        when(verificationRepository.findById("1")).thenReturn(Optional.of(existing));
        VerificationOverrideRequestDto request = new VerificationOverrideRequestDto();
        request.setCandidateId("1");
        request.setNewStatus("VERIFIED");

        service.overrideVerificationStatus(request, "admin");

        ArgumentCaptor<VerificationOverride> captor = ArgumentCaptor.forClass(VerificationOverride.class);
        verify(overrideRepository).save(captor.capture());
        assertThat(captor.getValue().getTenantId()).isEqualTo("resolved-tenant");
    }

    @Test
    void overrideVerificationStatusLeavesTenantIdNullWhenResolutionFails() {
        withUnresolvedTenant(() -> {
            VerificationResult existing = VerificationResult.builder().candidateId("1").build();
            when(verificationRepository.findById("1")).thenReturn(Optional.of(existing));
            VerificationOverrideRequestDto request = new VerificationOverrideRequestDto();
            request.setCandidateId("1");
            request.setNewStatus("VERIFIED");

            service.overrideVerificationStatus(request, "admin");

            ArgumentCaptor<VerificationOverride> captor = ArgumentCaptor.forClass(VerificationOverride.class);
            verify(overrideRepository).save(captor.capture());
            assertThat(captor.getValue().getTenantId()).isNull();
        });
    }

    @Test
    void overrideVerificationStatusSkipsFraudFlowWhenJdbcTemplateAbsent() {
        VerificationResult existing = VerificationResult.builder().candidateId("1").build();
        when(verificationRepository.findById("1")).thenReturn(Optional.of(existing));
        VerificationOverrideRequestDto request = new VerificationOverrideRequestDto();
        request.setCandidateId("1");
        request.setNewStatus("REJECTED");
        request.setFraudDetection(true);

        VerificationResultDto result = service.overrideVerificationStatus(request, "admin");

        assertThat(result.getVerificationStatus()).isEqualTo("REJECTED");
    }

    @Test
    void overrideVerificationStatusFraudDetectionUpdatesDownstreamTables() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        ReflectionTestUtils.setField(service, "jdbcTemplate", jdbcTemplate);
        VerificationResult existing = VerificationResult.builder().candidateId("42").build();
        when(verificationRepository.findById("42")).thenReturn(Optional.of(existing));
        VerificationOverrideRequestDto request = new VerificationOverrideRequestDto();
        request.setCandidateId("42");
        request.setNewStatus("REJECTED");
        request.setFraudDetection(true);
        request.setOverrideReason("fraud reason");

        service.overrideVerificationStatus(request, "admin");

        verify(jdbcTemplate, times(3)).update(anyString(), any(Object[].class));
    }

    @Test
    void overrideVerificationStatusFraudDetectionCatchesNonNumericCandidateId() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        ReflectionTestUtils.setField(service, "jdbcTemplate", jdbcTemplate);
        VerificationResult existing = VerificationResult.builder().candidateId("not-a-number").build();
        when(verificationRepository.findById("not-a-number")).thenReturn(Optional.of(existing));
        VerificationOverrideRequestDto request = new VerificationOverrideRequestDto();
        request.setCandidateId("not-a-number");
        request.setNewStatus("REJECTED");
        request.setFraudDetection(true);

        VerificationResultDto result = service.overrideVerificationStatus(request, "admin");

        assertThat(result).isNotNull();
        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void overrideVerificationStatusSkipsFraudFlowWhenStatusNotRejected() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        ReflectionTestUtils.setField(service, "jdbcTemplate", jdbcTemplate);
        VerificationResult existing = VerificationResult.builder().candidateId("1").build();
        when(verificationRepository.findById("1")).thenReturn(Optional.of(existing));
        VerificationOverrideRequestDto request = new VerificationOverrideRequestDto();
        request.setCandidateId("1");
        request.setNewStatus("VERIFIED");
        request.setFraudDetection(true);

        service.overrideVerificationStatus(request, "admin");

        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
    }
}

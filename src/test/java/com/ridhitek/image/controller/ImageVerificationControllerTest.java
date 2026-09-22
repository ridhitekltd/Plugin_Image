package com.ridhitek.image.controller;

import com.ridhitek.image.dto.VerificationOverrideDto;
import com.ridhitek.image.dto.VerificationOverrideRequestDto;
import com.ridhitek.image.dto.VerificationResultDto;
import com.ridhitek.image.service.ImageVerificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ImageVerificationControllerTest {

    private ImageVerificationService imageService;
    private ImageVerificationController controller;

    @BeforeEach
    void setUp() {
        imageService = mock(ImageVerificationService.class);
        controller = new ImageVerificationController();
        ReflectionTestUtils.setField(controller, "imageService", imageService);
    }

    @Test
    void getUnverifiedCandidatesReturnsServiceResult() {
        List<VerificationResultDto> data = List.of(VerificationResultDto.builder().candidateId("1").build());
        when(imageService.getUnverifiedCandidates()).thenReturn(data);

        ResponseEntity<List<VerificationResultDto>> response = controller.getUnverifiedCandidates();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(data);
    }

    @Test
    void getVerifiedCandidatesReturnsServiceResult() {
        List<VerificationResultDto> data = List.of(VerificationResultDto.builder().candidateId("2").build());
        when(imageService.getVerifiedCandidates()).thenReturn(data);

        ResponseEntity<List<VerificationResultDto>> response = controller.getVerifiedCandidates();

        assertThat(response.getBody()).isSameAs(data);
    }

    @Test
    void getVerificationStatusReturnsServiceResult() {
        VerificationResultDto dto = VerificationResultDto.builder().candidateId("1").verificationStatus("VERIFIED").build();
        when(imageService.getVerificationStatus("1")).thenReturn(dto);

        ResponseEntity<VerificationResultDto> response = controller.getVerificationStatus("1");

        assertThat(response.getBody()).isSameAs(dto);
    }

    @Test
    void getVerificationHistoryReturnsServiceResult() {
        List<VerificationOverrideDto> history = List.of(VerificationOverrideDto.builder().candidateId("1").build());
        when(imageService.getVerificationHistory("1")).thenReturn(history);

        ResponseEntity<List<VerificationOverrideDto>> response = controller.getVerificationHistory("1");

        assertThat(response.getBody()).isSameAs(history);
    }

    @Test
    void getCandidatePhotosReturnsServiceResult() {
        Map<String, Object> photos = Map.of("idPhotos", List.of("url1"));
        when(imageService.getCandidatePhotos("1")).thenReturn(photos);

        ResponseEntity<Map<String, Object>> response = controller.getCandidatePhotos("1");

        assertThat(response.getBody()).isSameAs(photos);
    }

    @Test
    void uploadPhotoDelegatesToService() throws Exception {
        MultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", "data".getBytes());
        VerificationResultDto dto = VerificationResultDto.builder().candidateId("1").build();
        when(imageService.uploadPhoto("1", "id", file)).thenReturn(dto);

        ResponseEntity<VerificationResultDto> response = controller.uploadPhoto("1", "id", file);

        assertThat(response.getBody()).isSameAs(dto);
    }

    @Test
    void overrideStatusUsesPrincipalNameAsAuditor() {
        VerificationOverrideRequestDto request = new VerificationOverrideRequestDto();
        request.setCandidateId("1");
        Principal principal = () -> "john.doe";
        VerificationResultDto dto = VerificationResultDto.builder().candidateId("1").build();
        when(imageService.overrideVerificationStatus(eq(request), eq("john.doe"))).thenReturn(dto);

        ResponseEntity<VerificationResultDto> response = controller.overrideStatus(request, principal);

        assertThat(response.getBody()).isSameAs(dto);
        verify(imageService).overrideVerificationStatus(request, "john.doe");
    }

    @Test
    void overrideStatusDefaultsToAdminWhenPrincipalNull() {
        VerificationOverrideRequestDto request = new VerificationOverrideRequestDto();
        request.setCandidateId("1");
        VerificationResultDto dto = VerificationResultDto.builder().candidateId("1").build();
        when(imageService.overrideVerificationStatus(any(), anyString())).thenReturn(dto);

        controller.overrideStatus(request, null);

        verify(imageService).overrideVerificationStatus(request, "ADMIN");
    }

    @Test
    void bulkUpdateDelegatesToService() {
        List<VerificationResultDto> results = List.of(VerificationResultDto.builder().candidateId("1").build());
        when(imageService.bulkUpdateVerificationStatus(results)).thenReturn(results);

        ResponseEntity<List<VerificationResultDto>> response = controller.bulkUpdate(results);

        assertThat(response.getBody()).isSameAs(results);
    }
}

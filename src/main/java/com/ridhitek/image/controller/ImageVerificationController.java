package com.ridhitek.image.controller;

import com.ridhitek.image.dto.VerificationOverrideRequestDto;
import com.ridhitek.image.dto.VerificationOverrideDto;
import com.ridhitek.image.dto.VerificationResultDto;
import com.ridhitek.image.service.ImageVerificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

@Slf4j
@RestController
@RequestMapping("/api/verification")
public class ImageVerificationController {

    @Autowired
    private ImageVerificationService imageService;

    // --- Listing & Retrieval ---

    @GetMapping("/unverified-candidates")
    @PreAuthorize("hasAnyAuthority('SCOPE_admin', 'ROLE_ADMIN')")
    public ResponseEntity<List<VerificationResultDto>> getUnverifiedCandidates() {
        log.info("Request for unverified candidates (Admin)");
        return ResponseEntity.ok(imageService.getUnverifiedCandidates());
    }

    @GetMapping("/verified-candidates")
    @PreAuthorize("hasAnyAuthority('SCOPE_admin', 'ROLE_ADMIN')")
    public ResponseEntity<List<VerificationResultDto>> getVerifiedCandidates() {
        log.info("Request for verified candidates (Admin)");
        return ResponseEntity.ok(imageService.getVerifiedCandidates());
    }

    @GetMapping("/status/{candidateId}")
    @PreAuthorize("hasAnyAuthority('SCOPE_admin', 'ROLE_ADMIN')")
    public ResponseEntity<VerificationResultDto> getVerificationStatus(@PathVariable String candidateId) {
        log.info("Request for verification status: {}", candidateId);
        return ResponseEntity.ok(imageService.getVerificationStatus(candidateId));
    }

    @GetMapping("/history/{candidateId}")
    @PreAuthorize("hasAnyAuthority('SCOPE_admin', 'ROLE_ADMIN')")
    public ResponseEntity<List<VerificationOverrideDto>> getVerificationHistory(@PathVariable String candidateId) {
        log.info("Request for verification history: {}", candidateId);
        return ResponseEntity.ok(imageService.getVerificationHistory(candidateId));
    }

    @GetMapping("/photos/{candidateId}")
    @PreAuthorize("hasAnyAuthority('SCOPE_admin', 'ROLE_ADMIN')")
    public ResponseEntity<Map<String, Object>> getCandidatePhotos(@PathVariable String candidateId) {
        log.info("Request for candidate photos: {}", candidateId);
        return ResponseEntity.ok(imageService.getCandidatePhotos(candidateId));
    }

    // --- Photo Uploads ---

    @PostMapping("/{candidateId}/upload/{stage}")
    @PreAuthorize("hasAnyAuthority('SCOPE_admin', 'ROLE_ADMIN')")
    public ResponseEntity<VerificationResultDto> uploadPhoto(
            @PathVariable String candidateId,
            @PathVariable String stage,
            @RequestParam("file") MultipartFile file) throws IOException {
        log.info("Request for photo upload: {}/{}", candidateId, stage);
        return ResponseEntity.ok(imageService.uploadPhoto(candidateId, stage, file));
    }

    // --- Overrides & Bulk ---

    @PostMapping("/override")
    @PreAuthorize("hasAnyAuthority('SCOPE_admin', 'ROLE_ADMIN')")
    public ResponseEntity<VerificationResultDto> overrideStatus(
            @RequestBody VerificationOverrideRequestDto request, Principal principal) {
        String auditorId = principal != null ? principal.getName() : "ADMIN";
        log.info("Admin override request: {}", request.getCandidateId());
        return ResponseEntity.ok(imageService.overrideVerificationStatus(request, auditorId));
    }

    @PostMapping("/bulk-status-update")
    @PreAuthorize("hasAnyAuthority('SCOPE_admin', 'ROLE_ADMIN')")
    public ResponseEntity<List<VerificationResultDto>> bulkUpdate(@RequestBody List<VerificationResultDto> results) {
        log.info("Request for bulk status update");
        return ResponseEntity.ok(imageService.bulkUpdateVerificationStatus(results));
    }
}

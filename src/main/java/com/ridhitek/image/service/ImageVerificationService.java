package com.ridhitek.image.service;

import com.ridhitek.image.dto.VerificationOverrideDto;
import com.ridhitek.image.dto.VerificationOverrideRequestDto;
import com.ridhitek.image.dto.VerificationResultDto;
import com.ridhitek.image.entity.VerificationOverride;
import com.ridhitek.image.entity.VerificationResult;
import com.ridhitek.image.repository.VerificationOverrideRepository;
import com.ridhitek.image.repository.VerificationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional
public class ImageVerificationService {

    @Autowired
    private VerificationRepository verificationRepository;

    @Autowired
    private VerificationOverrideRepository overrideRepository;

    @Autowired
    private StorageService storageService;

    // --- Status & Lists ---

    public List<VerificationResultDto> getUnverifiedCandidates() {
        return verificationRepository.findByVerificationStatusNot("VERIFIED").stream()
                .map(this::mapToDto).collect(Collectors.toList());
    }

    public List<VerificationResultDto> getVerifiedCandidates() {
        return verificationRepository.findByVerificationStatus("VERIFIED").stream()
                .map(this::mapToDto).collect(Collectors.toList());
    }

    public VerificationResultDto getVerificationStatus(String candidateId) {
        return verificationRepository.findById(candidateId)
                .map(this::mapToDto)
                .orElseGet(() -> VerificationResultDto.builder().candidateId(candidateId).verificationStatus("NOT_STARTED").build());
    }

    // --- Photo Uploads ---

    public VerificationResultDto uploadPhoto(String candidateId, String stage, MultipartFile file) throws IOException {
        String filePath = storageService.uploadFile(candidateId, stage, file);
        
        VerificationResult result = verificationRepository.findById(candidateId)
                .orElse(new VerificationResult());
        result.setCandidateId(candidateId);
        result.setVerificationStatus("PENDING_VERIFICATION");
        result.setUpdatedAt(LocalDateTime.now());

        switch (stage.toLowerCase()) {
            case "id" -> result.setGovernmentIdPath(filePath);
            case "candidate" -> result.setCandidatePhotoPath(filePath);
            case "l1" -> result.setL1PhotoPath(filePath);
            case "l2" -> result.setL2PhotoPath(filePath);
            case "l3" -> result.setL3PhotoPath(filePath);
        }

        return mapToDto(verificationRepository.save(result));
    }

    // --- Overrides & History ---

    public List<VerificationOverrideDto> getVerificationHistory(String candidateId) {
        return overrideRepository.findByCandidateIdOrderByCreatedAtDesc(candidateId).stream()
                .map(this::mapToOverrideDto).collect(Collectors.toList());
    }

    public VerificationResultDto overrideVerificationStatus(VerificationOverrideRequestDto request, String auditorId) {
        VerificationResult result = verificationRepository.findById(request.getCandidateId())
                .orElseThrow(() -> new RuntimeException("Candidate verification not found"));
        
        String oldStatus = result.getVerificationStatus();
        result.setVerificationStatus(request.getNewStatus());
        result.setUpdatedAt(LocalDateTime.now());
        
        // Log history
        VerificationOverride override = new VerificationOverride();
        override.setCandidateId(request.getCandidateId());
        override.setOldStatus(oldStatus);
        override.setNewStatus(request.getNewStatus());
        override.setOverrideReason(request.getOverrideReason());
        override.setAuditorId(auditorId);
        override.setCreatedAt(LocalDateTime.now());
        overrideRepository.save(override);

        return mapToDto(verificationRepository.save(result));
    }

    // --- Bulk Operations ---

    public List<VerificationResultDto> bulkUpdateVerificationStatus(List<VerificationResultDto> results) {
        return results.stream().map(dto -> {
            VerificationResult result = verificationRepository.findById(dto.getCandidateId()).orElse(new VerificationResult());
            result.setCandidateId(dto.getCandidateId());
            result.setVerificationStatus(dto.getVerificationStatus());
            result.setUpdatedAt(LocalDateTime.now());
            return mapToDto(verificationRepository.save(result));
        }).collect(Collectors.toList());
    }

    // --- Mappings ---

    private VerificationResultDto mapToDto(VerificationResult entity) {
        return VerificationResultDto.builder()
                .candidateId(entity.getCandidateId())
                .governmentIdPath(storageService.getFileViewUrl(entity.getGovernmentIdPath()))
                .candidatePhotoPath(storageService.getFileViewUrl(entity.getCandidatePhotoPath()))
                .l1PhotoPath(storageService.getFileViewUrl(entity.getL1PhotoPath()))
                .l2PhotoPath(storageService.getFileViewUrl(entity.getL2PhotoPath()))
                .l3PhotoPath(storageService.getFileViewUrl(entity.getL3PhotoPath()))
                .verificationStatus(entity.getVerificationStatus())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private VerificationOverrideDto mapToOverrideDto(VerificationOverride entity) {
        return VerificationOverrideDto.builder()
                .candidateId(entity.getCandidateId())
                .oldStatus(entity.getOldStatus())
                .newStatus(entity.getNewStatus())
                .overrideReason(entity.getOverrideReason())
                .auditorId(entity.getAuditorId())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}

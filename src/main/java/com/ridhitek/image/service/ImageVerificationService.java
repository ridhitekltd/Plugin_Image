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
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional
public class ImageVerificationService implements IImageVerificationService {

    @Autowired
    private VerificationRepository verificationRepository;

    @Autowired
    private VerificationOverrideRepository overrideRepository;

    @Autowired
    private StorageService storageService;

    @Override
    public List<VerificationResultDto> getUnverifiedCandidates() {
        return verificationRepository.findByVerificationStatusNot("VERIFIED").stream()
                .map(this::mapToDto).collect(Collectors.toList());
    }

    @Override
    public List<VerificationResultDto> getVerifiedCandidates() {
        return verificationRepository.findByVerificationStatus("VERIFIED").stream()
                .map(this::mapToDto).collect(Collectors.toList());
    }

    @Override
    public VerificationResultDto getVerificationStatus(String candidateId) {
        return verificationRepository.findById(candidateId)
                .map(this::mapToDto)
                .orElseGet(() -> VerificationResultDto.builder().candidateId(candidateId).verificationStatus("NOT_STARTED").build());
    }

    @Override
    public VerificationResultDto updateVerificationStatus(String candidateId, VerificationResultDto resultDto) {
        VerificationResult result = verificationRepository.findById(candidateId)
                .orElse(VerificationResult.builder().candidateId(candidateId).build());
        result.setVerificationStatus(resultDto.getVerificationStatus());
        result.setUpdatedAt(LocalDateTime.now());
        return mapToDto(verificationRepository.save(result));
    }

    @Override
    public List<VerificationResultDto> bulkUpdateVerificationStatus(List<VerificationResultDto> results) {
        return results.stream()
                .map(r -> updateVerificationStatus(r.getCandidateId(), r))
                .collect(Collectors.toList());
    }

    @Override
    public List<Object[]> getVerificationStatusCounts() {
        return verificationRepository.getVerificationStatusCounts();
    }

    @Override
    public Map<String, Object> uploadPhotos(String candidateId, String stage, String selectedIdType, 
                                           MultipartFile[] idImages, MultipartFile[] screenshots, 
                                           MultipartFile[] l1Images, MultipartFile[] l2Images, 
                                           MultipartFile[] l3Images) {
        log.info("Deep upload for candidate {}: stage={}", candidateId, stage);
        List<String> uploadedFiles = new ArrayList<>();

        if (idImages != null) {
            for (MultipartFile f : idImages) uploadedFiles.add("ID: " + uploadSingleFile(candidateId, "id", f));
        }
        if (screenshots != null) {
            for (MultipartFile f : screenshots) uploadedFiles.add("Candidate: " + uploadSingleFile(candidateId, "candidate", f));
        }
        if (l1Images != null) {
            for (MultipartFile f : l1Images) uploadedFiles.add("L1: " + uploadSingleFile(candidateId, "l1", f));
        }
        if (l2Images != null) {
            for (MultipartFile f : l2Images) uploadedFiles.add("L2: " + uploadSingleFile(candidateId, "l2", f));
        }
        if (l3Images != null) {
            for (MultipartFile f : l3Images) uploadedFiles.add("L3: " + uploadSingleFile(candidateId, "l3", f));
        }

        VerificationResult result = verificationRepository.findById(candidateId)
                .orElse(VerificationResult.builder().candidateId(candidateId).build());
        result.setVerificationStatus("PENDING_VERIFICATION");
        result.setUpdatedAt(LocalDateTime.now());
        verificationRepository.save(result);

        Map<String, Object> response = new HashMap<>();
        response.put("candidateId", candidateId);
        response.put("uploadedFiles", uploadedFiles);
        return response;
    }

    @Override
    public VerificationResultDto uploadPhoto(String candidateId, String stage, MultipartFile file) {
        log.info("Single photo upload for candidate {}: stage={}", candidateId, stage);
        String filePath;
        try {
            filePath = storageService.uploadFile(candidateId, stage, file);
        } catch (IOException e) {
            throw new RuntimeException("Failed to upload file", e);
        }

        VerificationResult result = verificationRepository.findById(candidateId)
                .orElse(VerificationResult.builder().candidateId(candidateId).build());

        String lowerStage = stage.toLowerCase();
        if (lowerStage.contains("id") || lowerStage.contains("government")) {
            result.setGovernmentIdPath(filePath);
        } else if (lowerStage.contains("candidate") || lowerStage.contains("photo")) {
            result.setCandidatePhotoPath(filePath);
        } else if (lowerStage.contains("l1")) {
            result.setL1PhotoPath(filePath);
        } else if (lowerStage.contains("l2")) {
            result.setL2PhotoPath(filePath);
        } else if (lowerStage.contains("l3")) {
            result.setL3PhotoPath(filePath);
        }

        result.setUpdatedAt(LocalDateTime.now());
        if (result.getVerificationStatus() == null || result.getVerificationStatus().equals("NOT_STARTED")) {
            result.setVerificationStatus("PENDING_VERIFICATION");
        }

        return mapToDto(verificationRepository.save(result));
    }

    @Override
    public Map<String, Object> getCandidatePhotos(String candidateId) {
        Map<String, Object> response = new HashMap<>();
        response.put("candidateId", candidateId);
        // Simplified for now, can list directories if needed
        return response;
    }

    @Override
    public String uploadSingleFile(String candidateId, String subFolder, MultipartFile file) {
        try {
            return storageService.uploadFile(candidateId, subFolder, file);
        } catch (IOException e) {
            throw new RuntimeException("Upload failed", e);
        }
    }

    @Override
    public List<VerificationOverrideDto> getVerificationHistory(String candidateId) {
        return overrideRepository.findByCandidateIdOrderByCreatedAtDesc(candidateId).stream()
                .map(this::mapToOverrideDto).collect(Collectors.toList());
    }

    @Override
    public VerificationResultDto overrideVerificationStatus(VerificationOverrideRequestDto request, String auditorId) {
        VerificationResult result = verificationRepository.findById(request.getCandidateId())
                .orElseThrow(() -> new RuntimeException("Candidate verification not found"));
        
        String oldStatus = result.getVerificationStatus();
        result.setVerificationStatus(request.getNewStatus());
        result.setUpdatedAt(LocalDateTime.now());
        
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

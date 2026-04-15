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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
    
    @Autowired(required = false)
    private StorageService storageService;

    @Value("${app.image.storage.path:D:/RIVO_10-02/Images}")
    private String BASE_STORAGE_PATH;

    @Value("${app.image.base.url:http://localhost:8181}")
    private String apiBaseUrl;

    @Override
    public List<VerificationResultDto> getUnverifiedCandidates() {
        return verificationRepository.findByVerificationStatus("PENDING_VERIFICATION").stream()
                .map(this::mapToDto).collect(Collectors.toList());
    }

    @Override
    public List<VerificationResultDto> getVerifiedCandidates() {
        return verificationRepository.findByVerificationStatusNot("PENDING_VERIFICATION").stream()
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
        
        updateEntityFromDto(result, resultDto);
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
        log.info("Original-style upload for candidate {}: stage={}", candidateId, stage);
        List<String> uploadedFiles = new ArrayList<>();

        if (idImages != null) for (MultipartFile f : idImages) uploadedFiles.add("ID: " + uploadSingleFile(candidateId, "id", f));
        if (screenshots != null) for (MultipartFile f : screenshots) uploadedFiles.add("Candidate: " + uploadSingleFile(candidateId, "candidate", f));
        
        boolean hasL1 = false, hasL2 = false, hasL3 = false;
        if (l1Images != null) { for (MultipartFile f : l1Images) { uploadSingleFile(candidateId, "l1", f); hasL1 = true; } }
        if (l2Images != null) { for (MultipartFile f : l2Images) { uploadSingleFile(candidateId, "l2", f); hasL2 = true; } }
        if (l3Images != null) { for (MultipartFile f : l3Images) { uploadSingleFile(candidateId, "l3", f); hasL3 = true; } }

        VerificationResult result = verificationRepository.findById(candidateId)
                .orElse(VerificationResult.builder().candidateId(candidateId).build());
        
        result.setVerificationStatus("PENDING_VERIFICATION");
        if (hasL1) result.setL1VerificationStatus("PENDING_VERIFICATION");
        if (hasL2) result.setL2VerificationStatus("PENDING_VERIFICATION");
        if (hasL3) result.setL3VerificationStatus("PENDING_VERIFICATION");
        
        verificationRepository.save(result);

        Map<String, Object> response = new HashMap<>();
        response.put("candidateId", candidateId);
        response.put("uploadedFiles", uploadedFiles);
        return response;
    }

    @Override
    public VerificationResultDto uploadPhoto(String candidateId, String stage, MultipartFile file) {
        String subFolder = stage.toLowerCase();
        if (subFolder.contains("id")) subFolder = "id";
        else if (subFolder.contains("candidate") || subFolder.contains("photo")) subFolder = "candidate";
        
        uploadSingleFile(candidateId, subFolder, file);

        VerificationResult result = verificationRepository.findById(candidateId)
                .orElse(VerificationResult.builder().candidateId(candidateId).build());

        result.setVerificationStatus("PENDING_VERIFICATION");
        if (subFolder.equals("l1")) result.setL1VerificationStatus("PENDING_VERIFICATION");
        if (subFolder.equals("l2")) result.setL2VerificationStatus("PENDING_VERIFICATION");
        if (subFolder.equals("l3")) result.setL3VerificationStatus("PENDING_VERIFICATION");

        return mapToDto(verificationRepository.save(result));
    }

    @Override
    public String uploadSingleFile(String candidateId, String subFolder, MultipartFile file) {
        if (file == null || file.isEmpty()) return null;
        try {
            // Use GCS storage service if available (gcp profile), otherwise local storage
            if (storageService != null) {
                log.info("Uploading to GCS for candidate {}, subfolder {}", candidateId, subFolder);
                String filePath = storageService.uploadFile(candidateId, subFolder, file);
                return filePath;
            } else {
                log.info("Uploading to local storage for candidate {}, subfolder {}", candidateId, subFolder);
                // Fallback to local storage
                String folderPath = BASE_STORAGE_PATH + "/candidate_" + candidateId + "/" + subFolder;
                File dir = new File(folderPath);
                
                // For stages, clear the folder to ensure replacement
                String subFolderLower = subFolder.toLowerCase();
                if (subFolderLower.equals("l1") || subFolderLower.equals("l2") || subFolderLower.equals("l3")) {
                    if (dir.exists()) {
                        File[] existingFiles = dir.listFiles();
                        if (existingFiles != null) {
                            for (File f : existingFiles) if (f.isFile()) f.delete();
                        }
                    }
                }

                if (!dir.exists()) dir.mkdirs();
                
                String fileName = file.getOriginalFilename();
                Path path = Paths.get(folderPath, fileName);
                Files.write(path, file.getBytes());
                return "candidate_" + candidateId + "/" + subFolder + "/" + fileName;
            }
        } catch (IOException e) {
            log.error("Failed to upload file for candidate {}: {}", candidateId, e.getMessage());
            throw new RuntimeException("File upload failed", e);
        }
    }

    @Override
    public Map<String, Object> getCandidatePhotos(String candidateId) {
        Map<String, Object> response = new HashMap<>();
        response.put("candidateId", candidateId);
        response.put("idPhotos", listPhotosAsUrls(candidateId, "id"));
        response.put("candidatePhotos", listPhotosAsUrls(candidateId, "candidate"));
        response.put("l1Photos", listPhotosAsUrls(candidateId, "l1"));
        response.put("l2Photos", listPhotosAsUrls(candidateId, "l2"));
        response.put("l3Photos", listPhotosAsUrls(candidateId, "l3"));
        return response;
    }

    private List<String> listPhotosAsUrls(String candidateId, String subFolder) {
        try {
            // Use GCS storage service if available (gcp profile), otherwise local storage
            if (storageService != null) {
                log.info("Listing photos from GCS for candidate {}, subfolder {}", candidateId, subFolder);
                return storageService.listFiles(candidateId, subFolder);
            } else {
                log.info("Listing photos from local storage for candidate {}, subfolder {}", candidateId, subFolder);
                // Fallback to local filesystem listing
                File dir = new File(BASE_STORAGE_PATH + "/candidate_" + candidateId + "/" + subFolder);
                if (!dir.exists() || !dir.isDirectory()) return Collections.emptyList();
                File[] files = dir.listFiles();
                if (files == null) return Collections.emptyList();
                return Arrays.stream(files)
                        .filter(f -> f.isFile() && isImageFile(f.getName()))
                        .map(f -> apiBaseUrl + "/api/image/view/candidate_" + candidateId + "/" + subFolder + "/" + f.getName())
                        .collect(Collectors.toList());
            }
        } catch (Exception e) {
            log.error("Error listing photos for candidate {}", candidateId, e);
            return Collections.emptyList();
        }
    }

    private boolean isImageFile(String fileName) {
        String lower = fileName.toLowerCase();
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".webp");
    }

    @Override
    public List<VerificationOverrideDto> getVerificationHistory(String candidateId) {
        return overrideRepository.findByCandidateIdOrderByOverriddenAtDesc(candidateId).stream()
                .map(this::mapToOverrideDto).collect(Collectors.toList());
    }

    @Override
    public VerificationResultDto overrideVerificationStatus(VerificationOverrideRequestDto request, String auditorId) {
        VerificationResult result = verificationRepository.findById(request.getCandidateId())
                .orElseThrow(() -> new RuntimeException("Candidate verification not found"));
        
        String oldStatus = result.getVerificationStatus();
        result.setVerificationStatus(request.getNewStatus());
        verificationRepository.save(result);
        
        VerificationOverride override = new VerificationOverride();
        override.setCandidateId(request.getCandidateId());
        override.setOldStatus(oldStatus);
        override.setNewStatus(request.getNewStatus());
        override.setOverrideReason(request.getOverrideReason());
        override.setAdminUserId(auditorId);
        overrideRepository.save(override);

        return mapToDto(result);
    }

    private Double formatMatchScore(Double score) {
        if (score == null) return null;
        if (score <= 1.0) {
            return score * 100.0;
        }
        return score;
    }

    private VerificationResultDto mapToDto(VerificationResult entity) {
        return VerificationResultDto.builder()
                .candidateId(entity.getCandidateId())
                .tenantId(entity.getTenantId())
                .verificationStatus(entity.getVerificationStatus())
                .overallConfidence(entity.getOverallConfidence())
                .matchScore(formatMatchScore(entity.getMatchScore()))
                .similarityPercentage(entity.getSimilarityPercentage())
                .processedAt(entity.getProcessedAt())
                .l1VerificationStatus(entity.getL1VerificationStatus())
                .l1MatchScore(formatMatchScore(entity.getL1MatchScore()))
                .l1SimilarityPercentage(entity.getL1SimilarityPercentage())
                .l1ProcessedAt(entity.getL1ProcessedAt())
                .l2VerificationStatus(entity.getL2VerificationStatus())
                .l2MatchScore(formatMatchScore(entity.getL2MatchScore()))
                .l2SimilarityPercentage(entity.getL2SimilarityPercentage())
                .l2ProcessedAt(entity.getL2ProcessedAt())
                .l3VerificationStatus(entity.getL3VerificationStatus())
                .l3MatchScore(formatMatchScore(entity.getL3MatchScore()))
                .l3SimilarityPercentage(entity.getL3SimilarityPercentage())
                .l3ProcessedAt(entity.getL3ProcessedAt())
                .build();
    }

    private VerificationOverrideDto mapToOverrideDto(VerificationOverride entity) {
        return VerificationOverrideDto.builder()
                .candidateId(entity.getCandidateId())
                .tenantId(entity.getTenantId())
                .oldStatus(entity.getOldStatus())
                .newStatus(entity.getNewStatus())
                .overrideReason(entity.getOverrideReason())
                .adminUserId(entity.getAdminUserId())
                .overriddenAt(entity.getOverriddenAt())
                .build();
    }

    private void updateEntityFromDto(VerificationResult entity, VerificationResultDto dto) {
        if (dto.getVerificationStatus() != null) entity.setVerificationStatus(dto.getVerificationStatus());
        if (dto.getOverallConfidence() != null) entity.setOverallConfidence(dto.getOverallConfidence());
        if (dto.getMatchScore() != null) entity.setMatchScore(dto.getMatchScore());
        if (dto.getSimilarityPercentage() != null) entity.setSimilarityPercentage(dto.getSimilarityPercentage());
        if (dto.getProcessedAt() != null) entity.setProcessedAt(dto.getProcessedAt());
        if (dto.getTenantId() != null) entity.setTenantId(dto.getTenantId());
        
        if (dto.getL1VerificationStatus() != null) entity.setL1VerificationStatus(dto.getL1VerificationStatus());
        if (dto.getL1MatchScore() != null) entity.setL1MatchScore(dto.getL1MatchScore());
        if (dto.getL1SimilarityPercentage() != null) entity.setL1SimilarityPercentage(dto.getL1SimilarityPercentage());
        if (dto.getL1ProcessedAt() != null) entity.setL1ProcessedAt(dto.getL1ProcessedAt());
        
        if (dto.getL2VerificationStatus() != null) entity.setL2VerificationStatus(dto.getL2VerificationStatus());
        if (dto.getL2MatchScore() != null) entity.setL2MatchScore(dto.getL2MatchScore());
        if (dto.getL2SimilarityPercentage() != null) entity.setL2SimilarityPercentage(dto.getL2SimilarityPercentage());
        if (dto.getL2ProcessedAt() != null) entity.setL2ProcessedAt(dto.getL2ProcessedAt());
        
        if (dto.getL3VerificationStatus() != null) entity.setL3VerificationStatus(dto.getL3VerificationStatus());
        if (dto.getL3MatchScore() != null) entity.setL3MatchScore(dto.getL3MatchScore());
        if (dto.getL3SimilarityPercentage() != null) entity.setL3SimilarityPercentage(dto.getL3SimilarityPercentage());
        if (dto.getL3ProcessedAt() != null) entity.setL3ProcessedAt(dto.getL3ProcessedAt());
    }
}

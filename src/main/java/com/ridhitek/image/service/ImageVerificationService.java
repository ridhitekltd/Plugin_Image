package com.ridhitek.image.service;

import com.ridhitek.image.dto.VerificationOverrideDto;
import com.ridhitek.image.dto.VerificationOverrideRequestDto;
import com.ridhitek.image.dto.VerificationResultDto;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Image Verification Service — RIVO Integration Mode
 *
 * All verification data is stored in RIVO's candidate_job_history table
 * via the RIVO Backend API (http://localhost:8181).
 * The primary key used is candidateJobId (integer).
 *
 * The old VerificationResult / VerificationOverride entities and their
 * databases (ridhitek_image_db) are NO LONGER USED.
 */
@Slf4j
@Service
@Transactional
public class ImageVerificationService implements IImageVerificationService {

    @Autowired(required = false)
    private StorageService storageService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RestTemplate restTemplate;

    @Value("${app.image.storage.path:D:/RIVO_10-02/Images}")
    private String BASE_STORAGE_PATH;

    @Value("${app.image.base.url:http://localhost:8082}")
    private String imageBaseUrl;

    @Value("${rivo.backend.url:http://localhost:8181}")
    private String rivoBackendUrl;

    // =========================================================
    // Listing & Retrieval — delegate to RIVO backend
    // =========================================================

    @Override
    public List<VerificationResultDto> getUnverifiedCandidates() {
        log.info("Fetching unverified candidates from RIVO backend");
        try {
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    rivoBackendUrl + "/api/candidates/unverified-candidates",
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<List<Map<String, Object>>>() {});
            if (response.getBody() == null) return Collections.emptyList();
            return response.getBody().stream()
                    .map(this::mapRivoResponseToDto)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Failed to fetch unverified candidates from RIVO: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public List<VerificationResultDto> getVerifiedCandidates() {
        log.info("Fetching verified candidates from RIVO backend");
        try {
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    rivoBackendUrl + "/api/v1/verified-candidates",
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<List<Map<String, Object>>>() {});
            if (response.getBody() == null) return Collections.emptyList();
            return response.getBody().stream()
                    .map(this::mapRivoResponseToDto)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Failed to fetch verified candidates from RIVO: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Get verification status for a candidateJobId (integer string).
     * Delegates to RIVO backend: GET /api/verification/status/{candidateJobId}
     */
    @Override
    public VerificationResultDto getVerificationStatus(String candidateJobId) {
        log.info("Fetching verification status directly from DB for candidateJobId: {}", candidateJobId);
        try {
            int id = Integer.parseInt(candidateJobId);
            String sql = "SELECT candidate_job_id, verification_status, overall_confidence, match_score, similarity_percentage, processed_at, " +
                         "l1_verification_status, l1_match_score, l1_similarity_percentage, l1_processed_at, " +
                         "l2_verification_status, l2_match_score, l2_similarity_percentage, l2_processed_at, " +
                         "l3_verification_status, l3_match_score, l3_similarity_percentage, l3_processed_at " +
                         "FROM candidate_job_history WHERE candidate_job_id = ?";

            List<VerificationResultDto> results = jdbcTemplate.query(sql, (rs, rowNum) -> {
                return VerificationResultDto.builder()
                        .candidateId(String.valueOf(rs.getInt("candidate_job_id")))
                        .verificationStatus(rs.getString("verification_status") != null ? rs.getString("verification_status") : "NOT_STARTED")
                        .overallConfidence(rs.getObject("overall_confidence") != null ? rs.getDouble("overall_confidence") : 0.0)
                        .matchScore(rs.getObject("match_score") != null ? rs.getDouble("match_score") : 0.0)
                        .similarityPercentage(rs.getObject("similarity_percentage") != null ? rs.getDouble("similarity_percentage") : 0.0)
                        .processedAt(rs.getTimestamp("processed_at") != null ? rs.getTimestamp("processed_at").toLocalDateTime() : null)
                        .l1VerificationStatus(rs.getString("l1_verification_status"))
                        .l1MatchScore(rs.getObject("l1_match_score") != null ? rs.getDouble("l1_match_score") : 0.0)
                        .l1SimilarityPercentage(rs.getObject("l1_similarity_percentage") != null ? rs.getDouble("l1_similarity_percentage") : 0.0)
                        .l1ProcessedAt(rs.getTimestamp("l1_processed_at") != null ? rs.getTimestamp("l1_processed_at").toLocalDateTime() : null)
                        .l2VerificationStatus(rs.getString("l2_verification_status"))
                        .l2MatchScore(rs.getObject("l2_match_score") != null ? rs.getDouble("l2_match_score") : 0.0)
                        .l2SimilarityPercentage(rs.getObject("l2_similarity_percentage") != null ? rs.getDouble("l2_similarity_percentage") : 0.0)
                        .l2ProcessedAt(rs.getTimestamp("l2_processed_at") != null ? rs.getTimestamp("l2_processed_at").toLocalDateTime() : null)
                        .l3VerificationStatus(rs.getString("l3_verification_status"))
                        .l3MatchScore(rs.getObject("l3_match_score") != null ? rs.getDouble("l3_match_score") : 0.0)
                        .l3SimilarityPercentage(rs.getObject("l3_similarity_percentage") != null ? rs.getDouble("l3_similarity_percentage") : 0.0)
                        .l3ProcessedAt(rs.getTimestamp("l3_processed_at") != null ? rs.getTimestamp("l3_processed_at").toLocalDateTime() : null)
                        .build();
            }, id);

            if (results.isEmpty()) {
                return VerificationResultDto.builder()
                        .candidateId(candidateJobId)
                        .verificationStatus("NOT_STARTED")
                        .build();
            }
            return results.get(0);
        } catch (Exception e) {
            log.warn("Could not fetch verification status from DB for candidateJobId={}: {}", candidateJobId, e.getMessage());
            return VerificationResultDto.builder()
                    .candidateId(candidateJobId)
                    .verificationStatus("NOT_STARTED")
                    .build();
        }
    }

    /**
     * Update verification status for a candidateJobId.
     * Calls RIVO backend: POST /api/candidates/{candidateJobId}/verification
     */
    @Override
    public VerificationResultDto updateVerificationStatus(String candidateJobId, VerificationResultDto resultDto) {
        log.info("Updating verification status directly in DB for candidateJobId: {} -> status: {}",
                candidateJobId, resultDto.getVerificationStatus());
        try {
            int id = Integer.parseInt(candidateJobId);
            String sql = "UPDATE candidate_job_history SET " +
                    "verification_status = COALESCE(?, verification_status), " +
                    "l1_verification_status = COALESCE(?, l1_verification_status), " +
                    "l1_match_score = COALESCE(?, l1_match_score), " +
                    "l1_similarity_percentage = COALESCE(?, l1_similarity_percentage), " +
                    "l2_verification_status = COALESCE(?, l2_verification_status), " +
                    "l2_match_score = COALESCE(?, l2_match_score), " +
                    "l2_similarity_percentage = COALESCE(?, l2_similarity_percentage), " +
                    "l3_verification_status = COALESCE(?, l3_verification_status), " +
                    "l3_match_score = COALESCE(?, l3_match_score), " +
                    "l3_similarity_percentage = COALESCE(?, l3_similarity_percentage) " +
                    "WHERE candidate_job_id = ?";

            int updated = jdbcTemplate.update(sql,
                    resultDto.getVerificationStatus(),
                    resultDto.getL1VerificationStatus(),
                    resultDto.getL1MatchScore(),
                    resultDto.getL1SimilarityPercentage(),
                    resultDto.getL2VerificationStatus(),
                    resultDto.getL2MatchScore(),
                    resultDto.getL2SimilarityPercentage(),
                    resultDto.getL3VerificationStatus(),
                    resultDto.getL3MatchScore(),
                    resultDto.getL3SimilarityPercentage(),
                    id);

            log.info("Direct DB status update for candidateJobId: {}, rows affected: {}", candidateJobId, updated);

            return resultDto;
        } catch (Exception e) {
            log.error("Failed to directly update verification status in DB for candidateJobId={}: {}", candidateJobId, e.getMessage());
            throw new RuntimeException("Failed to directly update verification status in DB: " + e.getMessage(), e);
        }
    }

    @Override
    public List<VerificationResultDto> bulkUpdateVerificationStatus(List<VerificationResultDto> results) {
        log.info("Bulk updating verification status for {} candidates", results.size());
        return results.stream()
                .map(r -> updateVerificationStatus(r.getCandidateId(), r))
                .collect(Collectors.toList());
    }

    @Override
    public List<Object[]> getVerificationStatusCounts() {
        // Not implemented via RIVO API — return empty list
        log.warn("getVerificationStatusCounts not supported in RIVO integration mode");
        return Collections.emptyList();
    }

    // =========================================================
    // Photo Upload — stored locally, then status set on RIVO
    // =========================================================

    @Override
    public Map<String, Object> uploadPhotos(String candidateJobId, String stage, String selectedIdType,
                                            MultipartFile[] idImages, MultipartFile[] screenshots,
                                            MultipartFile[] l1Images, MultipartFile[] l2Images,
                                            MultipartFile[] l3Images) {
        log.info("Uploading photos for candidateJobId={}, stage={}", candidateJobId, stage);
        List<String> uploadedFiles = new ArrayList<>();

        boolean hasL1 = false, hasL2 = false, hasL3 = false;

        if (idImages != null)     for (MultipartFile f : idImages)     uploadedFiles.add("ID: " + uploadSingleFile(candidateJobId, "id", f));
        if (screenshots != null)  for (MultipartFile f : screenshots)  uploadedFiles.add("Candidate: " + uploadSingleFile(candidateJobId, "candidate", f));
        if (l1Images != null)     for (MultipartFile f : l1Images)     { uploadSingleFile(candidateJobId, "l1", f); hasL1 = true; }
        if (l2Images != null)     for (MultipartFile f : l2Images)     { uploadSingleFile(candidateJobId, "l2", f); hasL2 = true; }
        if (l3Images != null)     for (MultipartFile f : l3Images)     { uploadSingleFile(candidateJobId, "l3", f); hasL3 = true; }

        // Update RIVO backend with PENDING_VERIFICATION status
        VerificationResultDto statusUpdate = VerificationResultDto.builder()
                .candidateId(candidateJobId)
                .verificationStatus("PENDING_VERIFICATION")
                .l1VerificationStatus(hasL1 ? "PENDING_VERIFICATION" : null)
                .l1MatchScore(hasL1 ? 0.0 : null)
                .l1SimilarityPercentage(hasL1 ? 0.0 : null)
                .l2VerificationStatus(hasL2 ? "PENDING_VERIFICATION" : null)
                .l2MatchScore(hasL2 ? 0.0 : null)
                .l2SimilarityPercentage(hasL2 ? 0.0 : null)
                .l3VerificationStatus(hasL3 ? "PENDING_VERIFICATION" : null)
                .l3MatchScore(hasL3 ? 0.0 : null)
                .l3SimilarityPercentage(hasL3 ? 0.0 : null)
                .build();

        try {
            updateVerificationStatus(candidateJobId, statusUpdate);
        } catch (Exception e) {
            log.warn("Could not update RIVO status after upload for candidateJobId={}: {}", candidateJobId, e.getMessage());
        }

        Map<String, Object> response = new HashMap<>();
        response.put("candidateJobId", candidateJobId);
        response.put("uploadedFiles", uploadedFiles);
        return response;
    }

    @Override
    public VerificationResultDto uploadPhoto(String candidateJobId, String stage, MultipartFile file) {
        String subFolder = stage.toLowerCase();
        if (subFolder.contains("id"))                                      subFolder = "id";
        else if (subFolder.contains("candidate") || subFolder.contains("photo")) subFolder = "candidate";

        uploadSingleFile(candidateJobId, subFolder, file);

        VerificationResultDto statusUpdate = VerificationResultDto.builder()
                .candidateId(candidateJobId)
                .verificationStatus("PENDING_VERIFICATION")
                .l1VerificationStatus("l1".equals(subFolder) ? "PENDING_VERIFICATION" : null)
                .l1MatchScore("l1".equals(subFolder) ? 0.0 : null)
                .l1SimilarityPercentage("l1".equals(subFolder) ? 0.0 : null)
                .l2VerificationStatus("l2".equals(subFolder) ? "PENDING_VERIFICATION" : null)
                .l2MatchScore("l2".equals(subFolder) ? 0.0 : null)
                .l2SimilarityPercentage("l2".equals(subFolder) ? 0.0 : null)
                .l3VerificationStatus("l3".equals(subFolder) ? "PENDING_VERIFICATION" : null)
                .l3MatchScore("l3".equals(subFolder) ? 0.0 : null)
                .l3SimilarityPercentage("l3".equals(subFolder) ? 0.0 : null)
                .build();

        try {
            return updateVerificationStatus(candidateJobId, statusUpdate);
        } catch (Exception e) {
            log.warn("RIVO status update failed after photo upload: {}", e.getMessage());
            return statusUpdate;
        }
    }

    @Override
    public String uploadSingleFile(String candidateJobId, String subFolder, MultipartFile file) {
        if (file == null || file.isEmpty()) return null;
        try {
            if (storageService != null) {
                log.info("Uploading to GCS for candidateJobId={}, subfolder={}", candidateJobId, subFolder);
                return storageService.uploadFile(candidateJobId, subFolder, file);
            } else {
                log.info("Uploading to local storage for candidateJobId={}, subfolder={}", candidateJobId, subFolder);
                String folderPath = BASE_STORAGE_PATH + "/candidate_" + candidateJobId + "/" + subFolder;
                File dir = new File(folderPath);

                String subFolderLower = subFolder.toLowerCase();
                if (subFolderLower.equals("l1") || subFolderLower.equals("l2") || subFolderLower.equals("l3")) {
                    if (dir.exists()) {
                        File[] existingFiles = dir.listFiles();
                        if (existingFiles != null) for (File f : existingFiles) if (f.isFile()) f.delete();
                    }
                }
                if (!dir.exists()) dir.mkdirs();

                String fileName = generateUniqueFileName(file.getOriginalFilename());
                Path path = Paths.get(folderPath, fileName);
                Files.write(path, file.getBytes());
                return "candidate_" + candidateJobId + "/" + subFolder + "/" + fileName;
            }
        } catch (IOException e) {
            log.error("Failed to upload file for candidateJobId={}: {}", candidateJobId, e.getMessage());
            throw new RuntimeException("File upload failed", e);
        }
    }

    @Override
    public Map<String, Object> getCandidatePhotos(String candidateJobId) {
        Map<String, Object> response = new HashMap<>();
        response.put("candidateJobId", candidateJobId);
        response.put("idPhotos",        listPhotosAsUrls(candidateJobId, "id"));
        response.put("candidatePhotos", listPhotosAsUrls(candidateJobId, "candidate"));
        response.put("l1Photos",        listPhotosAsUrls(candidateJobId, "l1"));
        response.put("l2Photos",        listPhotosAsUrls(candidateJobId, "l2"));
        response.put("l3Photos",        listPhotosAsUrls(candidateJobId, "l3"));
        return response;
    }

    private List<String> listPhotosAsUrls(String candidateJobId, String subFolder) {
        try {
            if (storageService != null) {
                return storageService.listFiles(candidateJobId, subFolder);
            } else {
                File dir = new File(BASE_STORAGE_PATH + "/candidate_" + candidateJobId + "/" + subFolder);
                if (!dir.exists() || !dir.isDirectory()) return Collections.emptyList();
                File[] files = dir.listFiles();
                if (files == null) return Collections.emptyList();
                return Arrays.stream(files)
                        .filter(f -> f.isFile() && isImageFile(f.getName()))
                        .map(f -> imageBaseUrl + "/api/image/view/candidate_" + candidateJobId + "/" + subFolder + "/" + f.getName())
                        .collect(Collectors.toList());
            }
        } catch (Exception e) {
            log.error("Error listing photos for candidateJobId={}", candidateJobId, e);
            return Collections.emptyList();
        }
    }

    private boolean isImageFile(String fileName) {
        String lower = fileName.toLowerCase();
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".webp");
    }

    // =========================================================
    // Override History — delegate to RIVO backend
    // =========================================================

    @Override
    public List<VerificationOverrideDto> getVerificationHistory(String candidateJobId) {
        log.info("Fetching verification history for candidateJobId: {}", candidateJobId);
        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    rivoBackendUrl + "/api/verification/history/" + candidateJobId,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<Map<String, Object>>() {});
            // History is inside response.data
            return Collections.emptyList(); // RIVO returns history inside ApiResponse wrapper
        } catch (Exception e) {
            log.warn("Could not fetch verification history from RIVO for candidateJobId={}: {}", candidateJobId, e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public VerificationResultDto overrideVerificationStatus(VerificationOverrideRequestDto request, String auditorId) {
        log.info("Override verification status for candidateJobId: {} by {}", request.getCandidateId(), auditorId);
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            // Build RIVO-compatible override request
            Map<String, String> overrideBody = new HashMap<>();
            overrideBody.put("candidateJobId", request.getCandidateId());
            overrideBody.put("newStatus", request.getNewStatus());
            overrideBody.put("overrideReason", request.getOverrideReason());

            HttpEntity<Map<String, String>> entity = new HttpEntity<>(overrideBody, headers);
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    rivoBackendUrl + "/api/verification/override",
                    HttpMethod.POST,
                    entity,
                    new ParameterizedTypeReference<Map<String, Object>>() {});

            if (response.getBody() != null && response.getBody().containsKey("data")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
                return mapRivoResponseToDto(data);
            }
        } catch (Exception e) {
            log.error("Override failed for candidateJobId={}: {}", request.getCandidateId(), e.getMessage());
            throw new RuntimeException("Override failed: " + e.getMessage(), e);
        }
        return VerificationResultDto.builder().candidateId(request.getCandidateId()).build();
    }

    // =========================================================
    // Helper: map RIVO API response fields → VerificationResultDto
    // =========================================================

    private String getAuthToken() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                return request.getHeader("Authorization");
            }
        } catch (Exception e) {
            log.warn("Could not retrieve Authorization header from request context: {}", e.getMessage());
        }
        return null;
    }

    private VerificationResultDto mapRivoResponseToDto(Map<String, Object> data) {
        if (data == null) return VerificationResultDto.builder().build();
        return VerificationResultDto.builder()
                .candidateId(getStr(data, "candidateJobId", "candidateId"))
                .verificationStatus(getStr(data, "verificationStatus"))
                .overallConfidence(getDbl(data, "overallConfidence"))
                .matchScore(getDbl(data, "matchScore"))
                .similarityPercentage(getDbl(data, "similarityPercentage"))
                .l1VerificationStatus(getStr(data, "l1VerificationStatus"))
                .l1MatchScore(getDbl(data, "l1MatchScore"))
                .l1SimilarityPercentage(getDbl(data, "l1SimilarityPercentage"))
                .l2VerificationStatus(getStr(data, "l2VerificationStatus"))
                .l2MatchScore(getDbl(data, "l2MatchScore"))
                .l2SimilarityPercentage(getDbl(data, "l2SimilarityPercentage"))
                .l3VerificationStatus(getStr(data, "l3VerificationStatus"))
                .l3MatchScore(getDbl(data, "l3MatchScore"))
                .l3SimilarityPercentage(getDbl(data, "l3SimilarityPercentage"))
                .build();
    }

    private String getStr(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Object val = map.get(key);
            if (val != null) return val.toString();
        }
        return null;
    }

    private Double getDbl(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val == null) return null;
        if (val instanceof Number) return ((Number) val).doubleValue();
        try { return Double.parseDouble(val.toString()); } catch (Exception e) { return null; }
    }

    private String generateUniqueFileName(String originalFilename) {
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        return LocalDateTime.now().toString().replace(":", "-") + "_" +
                UUID.randomUUID().toString().substring(0, 8) + extension;
    }
}

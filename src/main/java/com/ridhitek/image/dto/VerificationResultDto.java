package com.ridhitek.image.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerificationResultDto {
    private String candidateId;
    private String tenantId;
    private String verificationStatus;
    private Double overallConfidence;
    private Double matchScore;
    private Double similarityPercentage;
    private LocalDateTime processedAt;

    // L1
    private String l1VerificationStatus;
    private Double l1MatchScore;
    private Double l1SimilarityPercentage;
    private LocalDateTime l1ProcessedAt;

    // L2
    private String l2VerificationStatus;
    private Double l2MatchScore;
    private Double l2SimilarityPercentage;
    private LocalDateTime l2ProcessedAt;

    // L3
    private String l3VerificationStatus;
    private Double l3MatchScore;
    private Double l3SimilarityPercentage;
    private LocalDateTime l3ProcessedAt;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

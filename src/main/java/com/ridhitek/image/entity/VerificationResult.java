package com.ridhitek.image.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "verification_results")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerificationResult {

    @Id
    @Column(name = "candidate_id", nullable = false, unique = true)
    private String candidateId;

    @Column(name = "tenant_id")
    private String tenantId;

    @Column(name = "verification_status")
    private String verificationStatus;

    @Column(name = "overall_confidence")
    private Double overallConfidence;

    @Column(name = "match_score")
    private Double matchScore;

    @Column(name = "similarity_percentage")
    private Double similarityPercentage;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    // L1 Verification result fields
    @Column(name = "l1_verification_status")
    private String l1VerificationStatus;

    @Column(name = "l1_match_score")
    private Double l1MatchScore;

    @Column(name = "l1_similarity_percentage")
    private Double l1SimilarityPercentage;

    @Column(name = "l1_processed_at")
    private LocalDateTime l1ProcessedAt;

    // L2 Verification result fields
    @Column(name = "l2_verification_status")
    private String l2VerificationStatus;

    @Column(name = "l2_match_score")
    private Double l2MatchScore;

    @Column(name = "l2_similarity_percentage")
    private Double l2SimilarityPercentage;

    @Column(name = "l2_processed_at")
    private LocalDateTime l2ProcessedAt;

    // L3 Verification result fields
    @Column(name = "l3_verification_status")
    private String l3VerificationStatus;

    @Column(name = "l3_match_score")
    private Double l3MatchScore;

    @Column(name = "l3_similarity_percentage")
    private Double l3SimilarityPercentage;

    @Column(name = "l3_processed_at")
    private LocalDateTime l3ProcessedAt;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

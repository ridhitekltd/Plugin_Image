package com.ridhitek.image.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "verification_results")
@Data
public class VerificationResult {

    @Id
    @Column(name = "candidate_id", nullable = false, unique = true)
    private String candidateId;

    @Column(name = "government_id_path")
    private String governmentIdPath;

    @Column(name = "candidate_photo_path")
    private String candidatePhotoPath;

    @Column(name = "l1_photo_path")
    private String l1PhotoPath;

    @Column(name = "l2_photo_path")
    private String l2PhotoPath;

    @Column(name = "l3_photo_path")
    private String l3PhotoPath;

    @Column(name = "verification_status")
    private String verificationStatus; // e.g., PENDING_VERIFICATION, VERIFIED, REJECTED

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}

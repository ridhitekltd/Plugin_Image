package com.ridhitek.image.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "verification_overrides", indexes = {
        @Index(name = "idx_verification_candidate_id", columnList = "candidate_id")
})
@Data
@NoArgsConstructor
public class VerificationOverride {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "candidate_id", nullable = false)
    private String candidateId;

    @Column(name = "tenant_id")
    private String tenantId;

    @Column(name = "stage")
    private String stage;

    @Column(name = "old_status")
    private String oldStatus;

    @Column(name = "new_status")
    private String newStatus;

    @Column(name = "override_reason", columnDefinition = "TEXT")
    private String overrideReason;

    @Column(name = "admin_user_id")
    private String adminUserId;

    @Column(name = "overridden_at")
    private LocalDateTime overriddenAt;

    @PrePersist
    protected void onOverride() {
        overriddenAt = LocalDateTime.now();
    }
}

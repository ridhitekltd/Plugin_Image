package com.ridhitek.image.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "verification_overrides")
@Data
public class VerificationOverride {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "candidate_id", nullable = false)
    private String candidateId;

    @Column(name = "old_status")
    private String oldStatus;

    @Column(name = "new_status", nullable = false)
    private String newStatus;

    @Column(name = "override_reason")
    private String overrideReason;

    @Column(name = "auditor_id")
    private String auditorId;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}

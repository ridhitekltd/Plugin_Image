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
public class VerificationOverrideDto {
    private String candidateId;
    private String tenantId;
    private String oldStatus;
    private String newStatus;
    private String overrideReason;
    private String adminUserId;
    private LocalDateTime overriddenAt;
}

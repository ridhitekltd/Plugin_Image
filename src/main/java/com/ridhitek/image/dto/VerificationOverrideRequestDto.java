package com.ridhitek.image.dto;

import lombok.Data;

@Data
public class VerificationOverrideRequestDto {
    private String candidateId;
    private String newStatus;
    private String overrideReason;
}

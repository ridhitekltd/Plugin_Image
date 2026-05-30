package com.ridhitek.image.dto;

import lombok.Data;

@Data
public class VerificationOverrideRequestDto {
    private String candidateId;
    private String stage;
    private String newStatus;
    private String overrideReason;
    private String tenantId;

    /**
     * When true, this override is an explicit Identity Mismatch (fraud detection) action.
     * Only in this case should candidate_job_history.candidate_status be updated to 'Identity Mismatch'.
     * Regular Manual Review overrides should NOT touch candidate_job_history status.
     */
    private boolean fraudDetection;
}

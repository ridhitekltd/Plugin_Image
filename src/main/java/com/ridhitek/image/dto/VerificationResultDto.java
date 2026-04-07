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
    private String governmentIdPath;
    private String candidatePhotoPath;
    private String l1PhotoPath;
    private String l2PhotoPath;
    private String l3PhotoPath;
    private String verificationStatus;
    private LocalDateTime updatedAt;
}

package com.ridhitek.image.service;

import com.ridhitek.image.dto.VerificationOverrideDto;
import com.ridhitek.image.dto.VerificationOverrideRequestDto;
import com.ridhitek.image.dto.VerificationResultDto;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import java.util.Map;

public interface IImageVerificationService {

    VerificationResultDto getVerificationStatus(String candidateId);

    VerificationResultDto updateVerificationStatus(String candidateId, VerificationResultDto result);

    List<VerificationResultDto> bulkUpdateVerificationStatus(List<VerificationResultDto> results);

    List<VerificationOverrideDto> getVerificationHistory(String candidateId);

    VerificationResultDto overrideVerificationStatus(VerificationOverrideRequestDto request, String adminUserId);

    List<VerificationResultDto> getUnverifiedCandidates();

    List<VerificationResultDto> getVerifiedCandidates();

    List<Object[]> getVerificationStatusCounts();

    Map<String, Object> uploadPhotos(String candidateId, String stage, String selectedIdType, 
                                     MultipartFile[] idImages, MultipartFile[] screenshots, 
                                     MultipartFile[] l1Images, MultipartFile[] l2Images, 
                                     MultipartFile[] l3Images);
                                     
    VerificationResultDto uploadPhoto(String candidateId, String stage, MultipartFile file);

    Map<String, Object> getCandidatePhotos(String candidateId);
                                     
    String uploadSingleFile(String candidateId, String subFolder, MultipartFile file);
}

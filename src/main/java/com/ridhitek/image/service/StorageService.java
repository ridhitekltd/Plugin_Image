package com.ridhitek.image.service;

import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;

public interface StorageService {
    String uploadFile(String candidateId, String stage, MultipartFile file) throws IOException;
    String getFileViewUrl(String filePath);
}

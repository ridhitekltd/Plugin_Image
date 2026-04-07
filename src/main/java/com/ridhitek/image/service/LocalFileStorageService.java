package com.ridhitek.image.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
@Service
@Profile("!gcp")
public class LocalFileStorageService implements StorageService {

    @Value("${app.image.storage.path}")
    private String baseDir;

    @Value("${app.image.base.url:http://localhost:8181}")
    private String appBaseUrl;

    @Override
    public String uploadFile(String candidateId, String stage, MultipartFile file) throws IOException {
        Path directory = Paths.get(baseDir, candidateId, stage);
        if (!Files.exists(directory)) {
            Files.createDirectories(directory);
        }
        Path filePath = directory.resolve(file.getOriginalFilename());
        Files.copy(file.getInputStream(), filePath);
        log.info("Saved locally: {}", filePath);
        return candidateId + "/" + stage + "/" + file.getOriginalFilename();
    }

    @Override
    public String getFileViewUrl(String filePath) {
        // Return URL for the backend's image serving endpoint
        return appBaseUrl + "/api/image/view/" + filePath;
    }
}

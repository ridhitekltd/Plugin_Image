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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

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
        String candidateDir = candidateId.startsWith("candidate_") ? candidateId : "candidate_" + candidateId;
        Path directory = Paths.get(baseDir, candidateDir, stage);
        
        // If it's a verification stage, clear the directory first to ensure ONLY ONE image exists (Replacement)
        String stageLower = stage.toLowerCase();
        if (stageLower.equals("l1") || stageLower.equals("l2") || stageLower.equals("l3")) {
            if (Files.exists(directory)) {
                log.info("Clearing stage directory for replacement: {}", directory);
                File[] files = directory.toFile().listFiles();
                if (files != null) {
                    for (File f : files) {
                        if (f.isFile()) f.delete();
                    }
                }
            }
        }

        if (!Files.exists(directory)) {
            Files.createDirectories(directory);
        }
        Path filePath = directory.resolve(file.getOriginalFilename());
        Files.copy(file.getInputStream(), filePath);
        log.info("Saved locally: {}", filePath);
        return candidateDir + "/" + stage + "/" + file.getOriginalFilename();
    }

    @Override
    public String getFileViewUrl(String filePath) {
        // Return URL for the backend's image serving endpoint
        return appBaseUrl + "/api/image/view/" + filePath;
    }
    
    @Override
    public List<String> listFiles(String candidateId, String stage) {
        try {
            String candidateDir = candidateId.startsWith("candidate_") ? candidateId : "candidate_" + candidateId;
            File dir = new File(baseDir + "/" + candidateDir + "/" + stage);
            if (!dir.exists() || !dir.isDirectory()) return Collections.emptyList();
            File[] files = dir.listFiles();
            if (files == null) return Collections.emptyList();
            return Arrays.stream(files)
                    .filter(f -> f.isFile() && isImageFile(f.getName()))
                    .map(f -> getFileViewUrl(candidateDir + "/" + stage + "/" + f.getName()))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Error listing local files for {}/{}: {}", candidateId, stage, e.getMessage());
            return new ArrayList<>();
        }
    }
    
    private boolean isImageFile(String fileName) {
        String lower = fileName.toLowerCase();
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || 
               lower.endsWith(".png") || lower.endsWith(".webp");
    }
}

package com.ridhitek.image.service;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Slf4j
@Service
@Profile("gcp")
public class GcpFileStorageService implements StorageService {

    @Value("${gcp.storage.bucket-name}")
    private String bucketName;
    
    @Value("${app.image.base.url:https://storage.googleapis.com}")
    private String imageBaseUrl;

    private final Storage storage = StorageOptions.getDefaultInstance().getService();

    @Override
    public String uploadFile(String candidateId, String stage, MultipartFile file) throws IOException {
        String prefix = candidateId + "/" + stage + "/";
        
        // For verification stages, ensure only one image exists by deleting previous ones
        String stageLower = stage.toLowerCase();
        if (stageLower.equals("l1") || stageLower.equals("l2") || stageLower.equals("l3")) {
            log.info("Checking for existing GCS blobs to replace in: {}", prefix);
            Iterable<Blob> blobs = storage.list(bucketName, Storage.BlobListOption.prefix(prefix)).iterateAll();
            for (Blob blob : blobs) {
                log.info("Deleting existing blob for replacement: {}", blob.getName());
                storage.delete(blob.getBlobId());
            }
        }

        String blobName = prefix + file.getOriginalFilename();
        BlobId blobId = BlobId.of(bucketName, blobName);
        BlobInfo blobInfo = BlobInfo.newBuilder(blobId).setContentType(file.getContentType()).build();
        storage.create(blobInfo, file.getBytes());
        log.info("Uploaded to GCS: {}", blobName);
        return blobName;
    }

    @Override
    public String getFileViewUrl(String filePath) {
        // Return public URL since bucket is public
        // imageBaseUrl already includes the bucket name (e.g., https://storage.googleapis.com/hireguard-images)
        return imageBaseUrl + "/" + filePath;
    }
    
    @Override
    public List<String> listFiles(String candidateId, String stage) {
        try {
            String prefix = candidateId + "/" + stage + "/";
            log.info("Listing files from GCS: bucket={}, prefix={}", bucketName, prefix);
            
            Iterable<Blob> blobs = storage.list(bucketName, 
                Storage.BlobListOption.prefix(prefix)).iterateAll();
            
            List<String> fileUrls = StreamSupport.stream(blobs.spliterator(), false)
                .filter(blob -> !blob.getName().endsWith("/") && isImageFile(blob.getName()))
                .map(blob -> getFileViewUrl(blob.getName()))
                .collect(Collectors.toList());
            
            log.info("Found {} images in GCS for {}/{}", fileUrls.size(), candidateId, stage);
            return fileUrls;
        } catch (Exception e) {
            log.error("Failed to list files from GCS for {}/{}: {}", candidateId, stage, e.getMessage());
            return new ArrayList<>();
        }
    }
    
    private boolean isImageFile(String fileName) {
        String lower = fileName.toLowerCase();
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || 
               lower.endsWith(".png") || lower.endsWith(".webp");
    }
}

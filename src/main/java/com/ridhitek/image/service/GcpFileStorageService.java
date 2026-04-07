package com.ridhitek.image.service;

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
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@Profile("gcp")
public class GcpFileStorageService implements StorageService {

    @Value("${gcp.storage.bucket-name}")
    private String bucketName;

    private final Storage storage = StorageOptions.getDefaultInstance().getService();

    @Override
    public String uploadFile(String candidateId, String stage, MultipartFile file) throws IOException {
        String blobName = candidateId + "/" + stage + "/" + file.getOriginalFilename();
        BlobId blobId = BlobId.of(bucketName, blobName);
        BlobInfo blobInfo = BlobInfo.newBuilder(blobId).setContentType(file.getContentType()).build();
        storage.create(blobInfo, file.getBytes());
        log.info("Uploaded to GCS: {}", blobName);
        return blobName;
    }

    @Override
    public String getFileViewUrl(String filePath) {
        try {
            BlobId blobId = BlobId.of(bucketName, filePath);
            // Generate signed URL valid for 15 minutes
            URL signedUrl = storage.signUrl(
                    BlobInfo.newBuilder(blobId).build(),
                    15,
                    TimeUnit.MINUTES,
                    Storage.SignUrlOption.withV4Signature()
            );
            return signedUrl.toString();
        } catch (Exception e) {
            log.error("Failed to generate signed URL for {}: {}", filePath, e.getMessage());
            return null;
        }
    }
}

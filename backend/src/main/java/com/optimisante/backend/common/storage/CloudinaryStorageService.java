package com.optimisante.backend.common.storage;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CloudinaryStorageService implements StorageService {

    private final Cloudinary cloudinary;

    @Override
    public String uploadFile(byte[] bytes, String fileName, String folderPath) {
        try {
            // Generate a unique public ID to prevent overwriting files with the same name
            String publicId = UUID.randomUUID().toString() + "_" + fileName;

            Map<String, Object> uploadParams = ObjectUtils.asMap(
                    "folder", folderPath,
                    "public_id", publicId,
                    "resource_type", "auto" // Automatically detect if it's an image, video, or raw file
            );

            Map<?, ?> uploadResult = cloudinary.uploader().upload(bytes, uploadParams);
            return uploadResult.get("secure_url").toString();
            
        } catch (IOException e) {
            log.error("Failed to upload file to Cloudinary: {}", e.getMessage(), e);
            throw new RuntimeException("Could not upload file to storage", e);
        }
    }

    @Override
    public String generatePresignedOrSignedUrl(String publicId, int expirationMinutes) {
        try {
            long expiresAt = Instant.now().getEpochSecond() + (expirationMinutes * 60L);
            
            // Generate a signed URL for a private or authenticated resource
            return cloudinary.url()
                    .resourceType("auto")
                    .type("authenticated")
                    .signed(true)
                    .generate(publicId);
                    
        } catch (Exception e) {
            log.error("Failed to generate signed URL for publicId {}: {}", publicId, e.getMessage(), e);
            throw new RuntimeException("Could not generate signed URL", e);
        }
    }

    @Override
    public void deleteFile(String publicId) {
        try {
            Map<String, Object> deleteParams = ObjectUtils.asMap("resource_type", "auto");
            cloudinary.uploader().destroy(publicId, deleteParams);
        } catch (IOException e) {
            log.error("Failed to delete file from Cloudinary (publicId: {}): {}", publicId, e.getMessage(), e);
            throw new RuntimeException("Could not delete file from storage", e);
        }
    }
}

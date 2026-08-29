package com.optimisante.backend.common.storage;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
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
                    // "raw" (et non "auto") : generatePresignedOrSignedUrl() doit connaître le
                    // resource_type exact au moment de reconstruire l'URL de téléchargement, et
                    // "auto" n'est valide qu'à l'upload, pas pour une transformation/URL.
                    "resource_type", "raw",
                    "type", "upload" // "authenticated" nécessite une fonctionnalité de contrôle d'accès
                                      // non activée sur ce compte Cloudinary (erreur "deny or ACL failure").
                                      // "upload" + URL signée (voir generatePresignedOrSignedUrl) fonctionne
                                      // sur tout compte standard, sans configuration supplémentaire.
            );

            Map<?, ?> uploadResult = cloudinary.uploader().upload(bytes, uploadParams);
            return uploadResult.get("public_id").toString();

        } catch (IOException e) {
            log.error("Failed to upload file to Cloudinary: {}", e.getMessage(), e);
            throw new RuntimeException("Could not upload file to storage", e);
        }
    }

    @Override
    public String uploadFile(org.springframework.web.multipart.MultipartFile file, String folderPath) {
        try {
            return uploadFile(file.getBytes(), file.getOriginalFilename(), folderPath);
        } catch (IOException e) {
            log.error("Failed to read MultipartFile: {}", e.getMessage(), e);
            throw new RuntimeException("Could not read file for upload", e);
        }
    }

    @Override
    public String uploadGeneratedPdf(byte[] pdfBytes, String folderPath, String fileName) {
        try {
            Map<String, Object> uploadParams = ObjectUtils.asMap(
                    "folder", folderPath,
                    "public_id", fileName,
                    "resource_type", "raw", // PDFs can be uploaded as raw or image, but raw is safer for documents
                    "type", "upload" // "authenticated" nécessite une fonctionnalité de contrôle d'accès
                                      // non activée sur ce compte Cloudinary (erreur "deny or ACL failure").
                                      // "upload" + URL signée (voir generatePresignedOrSignedUrl) fonctionne
                                      // sur tout compte standard, sans configuration supplémentaire.
            );

            Map<?, ?> uploadResult = cloudinary.uploader().upload(pdfBytes, uploadParams);
            return uploadResult.get("public_id").toString();

        } catch (IOException e) {
            log.error("Failed to upload generated PDF to Cloudinary: {}", e.getMessage(), e);
            throw new RuntimeException("Could not upload PDF to storage", e);
        }
    }

    @Override
    public String generatePresignedOrSignedUrl(String publicId, int expirationMinutes) {
        try {
            // Generate a signed URL — le paramètre expirationMinutes n'est pas exploité tant que le
            // compte Cloudinary n'a pas la fonctionnalité "Authenticated"/token-based access activée
            // (cf. commentaire dans uploadFile/uploadGeneratedPdf) ; à revoir si cette fonctionnalité
            // est activée côté compte pour de vrais liens à expiration.
            return cloudinary.url()
                    .resourceType("raw")
                    .type("upload")
                    .signed(true)
                    .generate(publicId);
                    
        } catch (Exception e) {
            log.error("Failed to generate signed URL for publicId {}: {}", publicId, e.getMessage(), e);
            throw new RuntimeException("Could not generate signed URL", e);
        }
    }

    @Override
    public String uploadMedia(org.springframework.web.multipart.MultipartFile file, String folderPath, String resourceType) {
        try {
            String publicId = UUID.randomUUID().toString();
            Map<String, Object> uploadParams = ObjectUtils.asMap(
                    "folder", folderPath,
                    "public_id", publicId,
                    "resource_type", resourceType, // "image" ou "video" : nécessaire pour un rendu
                                                    // inline (<img>/<video>) et les transformations,
                                                    // contrairement à "raw" utilisé pour les documents.
                    "type", "upload"
            );
            Map<?, ?> uploadResult = cloudinary.uploader().upload(file.getBytes(), uploadParams);
            return uploadResult.get("public_id").toString();
        } catch (IOException e) {
            log.error("Failed to upload media to Cloudinary: {}", e.getMessage(), e);
            throw new RuntimeException("Could not upload media to storage", e);
        }
    }

    @Override
    public String generateMediaUrl(String publicId, String resourceType) {
        return cloudinary.url()
                .resourceType(resourceType)
                .type("upload")
                .generate(publicId);
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

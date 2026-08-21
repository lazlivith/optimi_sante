package com.optimisante.backend.common.storage;

public interface StorageService {
    
    /**
     * Upload a file to the storage provider.
     * 
     * @param bytes The file content as a byte array
     * @param fileName The original file name
     * @param folderPath The target folder path in the storage provider
     * @return The secure URL to access the uploaded file
     */
    String uploadFile(byte[] bytes, String fileName, String folderPath);

    /**
     * Upload a MultipartFile to the storage provider.
     * 
     * @param file The file to upload
     * @param folderPath The target folder path in the storage provider
     * @return The secure URL to access the uploaded file
     */
    String uploadFile(org.springframework.web.multipart.MultipartFile file, String folderPath);

    /**
     * Upload a generated PDF (from bytes) directly to the storage provider.
     * 
     * @param pdfBytes The generated PDF byte array
     * @param folderPath The target folder path (e.g. "docs/devis")
     * @param fileName The specific file name (e.g. "DEV-2026-0001")
     * @return The public_id or secure URL to access the uploaded file
     */
    String uploadGeneratedPdf(byte[] pdfBytes, String folderPath, String fileName);

    /**
     * Generate a presigned or signed URL for temporary access to a private resource.
     * 
     * @param publicId The public ID or path of the resource
     * @param expirationMinutes The duration in minutes before the URL expires
     * @return The temporary secure URL
     */
    String generatePresignedOrSignedUrl(String publicId, int expirationMinutes);

    /**
     * Delete a file from the storage provider.
     * 
     * @param publicId The public ID or path of the resource to delete
     */
    void deleteFile(String publicId);
}

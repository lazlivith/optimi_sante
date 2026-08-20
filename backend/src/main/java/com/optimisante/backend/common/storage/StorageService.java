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

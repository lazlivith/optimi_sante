package com.optimisante.backend.common.storage;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DigitalVaultService {

    private final Cloudinary cloudinary;

    /**
     * Calcule le hash SHA-256 d'un tableau d'octets.
     */
    private String calculateSha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedhash = digest.digest(data);
            StringBuilder hexString = new StringBuilder(2 * encodedhash.length);
            for (byte b : encodedhash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not found", e);
        }
    }

    /**
     * Upload le document PDF sécurisé vers le vault Cloudinary.
     * @param pdfBytes le fichier binaire
     * @param conventionId UUID de la convention
     * @param tenantId identifiant du tenant
     * @return un objet contenant le publicId et le hash calculé
     */
    public VaultUploadResult uploadConvention(byte[] pdfBytes, String conventionId, String tenantId) {
        String sha256Checksum = calculateSha256(pdfBytes);
        String publicId = "conv_" + conventionId;
        String folderPath = "vault/conventions";

        try {
            Map<String, Object> uploadParams = ObjectUtils.asMap(
                    "folder", folderPath,
                    "public_id", publicId,
                    "resource_type", "raw", // For PDF/Documents preservation
                    "context", "sha256_checksum=" + sha256Checksum + "|tenant_id=" + tenantId + "|convention_uuid=" + conventionId
            );

            Map<?, ?> uploadResult = cloudinary.uploader().upload(pdfBytes, uploadParams);
            String secureUrl = uploadResult.get("secure_url").toString();
            String uploadedPublicId = uploadResult.get("public_id").toString();
            
            log.info("Document successfully uploaded to vault: {} with hash {}", uploadedPublicId, sha256Checksum);
            return new VaultUploadResult(uploadedPublicId, secureUrl, sha256Checksum);
            
        } catch (IOException e) {
            log.error("Failed to upload document to digital vault: {}", e.getMessage(), e);
            throw new RuntimeException("Could not upload document to vault", e);
        }
    }

    /**
     * Génère l'URL Cloudinary sécurisée pour consulter ou télécharger le fichier.
     */
    public String getVaultDocumentUrl(String publicId, int expirationMinutes) {
        try {
            long expiresAt = Instant.now().getEpochSecond() + (expirationMinutes * 60L);
            return cloudinary.url()
                    .resourceType("raw")
                    .type("authenticated") // Nécessite une signature
                    .signed(true)
                    .generate(publicId);
        } catch (Exception e) {
            log.error("Failed to generate secure URL for vault document {}: {}", publicId, e.getMessage(), e);
            throw new RuntimeException("Could not generate vault document URL", e);
        }
    }

    /**
     * Vérification d'intégrité logicielle : compare un document téléchargé avec son hash d'origine.
     */
    public boolean verifyIntegrity(byte[] documentBytes, String expectedHash) {
        String calculatedHash = calculateSha256(documentBytes);
        return calculatedHash.equalsIgnoreCase(expectedHash);
    }

    public record VaultUploadResult(String publicId, String secureUrl, String sha256Hash) {}
}

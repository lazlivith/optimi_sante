package com.optimisante.backend.common.storage;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CloudinaryStorageService implements StorageService {

    private final Cloudinary cloudinary;

    /** Partagé : un client HTTP est conçu pour être réutilisé, en créer un par appel fuit. */
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @Override
    public String uploadFile(byte[] bytes, String fileName, String folderPath) {
        try {
            // Identifiant unique, pour qu'un fichier n'en ecrase jamais un autre.
            //
            // L'EXTENSION EST RETIREE, et ce n'est pas cosmetique : ce compte Cloudinary a la
            // livraison des PDF desactivee, et toute ressource dont la cle finit par « .pdf »
            // est refusee avec « deny or ACL failure ». Verifie par experience — meme contenu,
            // depose deux fois, la version avec extension est la seule des deux a etre refusee.
            // Aucun document televerse par un utilisateur n'etait donc telechargeable.
            //
            // Rien n'est perdu : le type reel est deduit du contenu a la diffusion, et le nom
            // propose a l'enregistrement vient du jeton, pas de la cle de stockage.
            String publicId = UUID.randomUUID() + "_" + sansExtension(fileName);

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

    /** Retire l'extension d'un nom de fichier : « passeport.pdf » devient « passeport ». */
    private static String sansExtension(String nomFichier) {
        if (nomFichier == null) return "fichier";
        int point = nomFichier.lastIndexOf('.');
        String base = point > 0 ? nomFichier.substring(0, point) : nomFichier;
        return base.isBlank() ? "fichier" : base;
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
            // Suffixe aléatoire : sans lui, la clé se reconstitue à partir du numéro de commande
            // (RECEIPT-OPT-20260829-9CEF), soit 65 536 possibilités par jour — assez peu pour
            // être énumérées. La clé reste lisible pour l'exploitation, mais plus prévisible.
            String publicId = fileName + "-" + UUID.randomUUID().toString().substring(0, 12);

            Map<String, Object> uploadParams = ObjectUtils.asMap(
                    "folder", folderPath,
                    "public_id", publicId,
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
    public String uploadPublicTemplate(byte[] bytes, String folderPath, String fileName) {
        try {
            // Clé volontairement STABLE : ce modèle vierge est régénéré à chaque demande d'un
            // prospect. Avec une clé aléatoire, chaque téléchargement laisserait un fichier de
            // plus dans le stockage, sans fin. Le document ne désigne personne.
            Map<String, Object> uploadParams = ObjectUtils.asMap(
                    "folder", folderPath,
                    "public_id", fileName,
                    "resource_type", "raw",
                    "type", "upload",
                    "overwrite", true,
                    "invalidate", true);

            Map<?, ?> resultat = cloudinary.uploader().upload(bytes, uploadParams);
            return resultat.get("public_id").toString();

        } catch (IOException e) {
            log.error("Failed to upload public template to Cloudinary: {}", e.getMessage(), e);
            throw new RuntimeException("Could not upload public template", e);
        }
    }

    @Override
    public byte[] download(String publicId) {
        // L'URL signée sert ici de simple adresse de lecture côté serveur : elle ne sort pas
        // de la JVM, contrairement à l'usage qu'on en faisait avant, où elle partait au
        // navigateur et laissait le document lisible par quiconque la détenait.
        String url = generatePresignedOrSignedUrl(publicId, 1);
        try {
            HttpRequest requete = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();
            HttpResponse<byte[]> reponse = HTTP.send(requete, HttpResponse.BodyHandlers.ofByteArray());
            if (reponse.statusCode() != 200) {
                throw new IllegalStateException(
                        "Le stockage a répondu " + reponse.statusCode() + " pour " + publicId);
            }
            return reponse.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Lecture du document interrompue : " + publicId, e);
        } catch (Exception e) {
            throw new RuntimeException("Document illisible au stockage : " + publicId, e);
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

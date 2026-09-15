package com.optimisante.backend.common.storage;

import org.springframework.web.multipart.MultipartFile;

/**
 * Stockage des fichiers de la plateforme, indépendant du fournisseur.
 *
 * <p>Les appelants désignent un {@link DossierStockage}, jamais un chemin : la racine,
 * l'environnement et la façon de conserver le fichier (document signé, image, vidéo) relèvent de
 * l'implémentation ({@code cloudinary.CloudinaryStorageService}).</p>
 *
 * <p>Toutes les méthodes renvoient la <b>clé de stockage</b> (public_id), à conserver en base :
 * c'est elle, et non une URL, qui permet ensuite de produire un lien.</p>
 */
public interface StorageService {

    /** Dépose un document ; son nom d'origine ne sert qu'à rendre la clé lisible. */
    String uploadFile(byte[] bytes, String fileName, DossierStockage dossier);

    String uploadFile(MultipartFile file, DossierStockage dossier);

    /** Dépose un PDF produit par la plateforme (convention, reçu, devis…). */
    String uploadGeneratedPdf(byte[] pdfBytes, DossierStockage dossier, String fileName);

    /**
     * Dépose un modèle vierge sous une clé STABLE, écrasée à chaque régénération : un modèle
     * ne désigne personne, et une clé aléatoire laisserait un fichier de plus à chaque demande.
     */
    String uploadPublicTemplate(byte[] bytes, DossierStockage dossier, String fileName);

    /** Dépose une image ou une vidéo affichée dans une page ; le type vient du dossier. */
    String uploadMedia(MultipartFile file, DossierStockage dossier);

    /** Lien signé d'un document. */
    String generatePresignedOrSignedUrl(String publicId, int expirationMinutes);

    /** Contenu d'un document, lu côté serveur (le lien ne quitte pas la JVM). */
    byte[] download(String publicId);

    /** Adresse publique d'une image ou d'une vidéo ({@code "image"} ou {@code "video"}). */
    String generateMediaUrl(String publicId, String resourceType);

    /** Supprime une image ou une vidéo. */
    void deleteFile(String publicId);

    /**
     * Supprime un document déposé par {@link #uploadFile} ou {@link #uploadGeneratedPdf}.
     *
     * <p>Distinct de {@link #deleteFile} : la suppression d'un document doit nommer son type de
     * ressource, que le fournisseur refuse de deviner.</p>
     */
    void deleteDocument(String publicId);
}

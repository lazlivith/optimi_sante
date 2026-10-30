package com.optimisante.backend.domain.document.controller;

import com.optimisante.backend.common.storage.ControleFichier;
import com.optimisante.backend.common.storage.DossierStockage;
import com.optimisante.backend.common.storage.StorageService;
import com.optimisante.backend.domain.document.service.DocumentAccessTokenService;
import com.optimisante.backend.domain.document.service.DocumentLinkService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.Set;

/**
 * Dépôt générique d'un document, utilisé par l'inscription d'un médecin à une formation.
 *
 * <p><b>Deux corrections.</b> Le dossier cible était une chaîne libre ({@code folder}, « general »
 * par défaut) : n'importe quel administrateur pouvait écrire n'importe où dans le compte
 * Cloudinary, partagé avec d'autres projets. Et l'endpoint était réservé aux administrateurs
 * alors que son seul appelant est l'écran d'inscription du MÉDECIN, qui recevait 403 à chaque
 * pièce. Le dossier est désormais choisi dans une liste fermée, et le médecin y a accès.</p>
 */
@RestController
@RequiredArgsConstructor
public class StorageController {

    /** Seuls dossiers ouverts à ce dépôt générique ; tout le reste passe par un endpoint métier. */
    private static final Set<DossierStockage> DOSSIERS_AUTORISES = Set.of(DossierStockage.DOSSIERS_PIECES);

    private final StorageService storageService;
    private final DocumentAccessTokenService jetons;

    /**
     * {@code /api/v1/storage/upload} est l'adresse à utiliser ; {@code /api/v1/admin/storage/upload}
     * reste servie pour un écran encore en cache dans un navigateur.
     *
     * <p><b>Ne rend plus le {@code public_id} (DP-4).</b> Il livrait au navigateur l'adresse brute
     * d'un objet Cloudinary. À la place : une <b>référence signée</b> à courte durée — que
     * l'inscription renvoie au serveur, qui la résout en clé — et une URL d'aperçu passant par
     * notre backend ({@code /api/v1/documents/file/<jeton>}), jamais Cloudinary. La clé ne quitte
     * pas le serveur.</p>
     */
    @PostMapping({"/api/v1/storage/upload", "/api/v1/admin/storage/upload"})
    @PreAuthorize("hasAnyRole('MEDECIN', 'ADMIN_ECOMMERCE', 'ADMIN_MOBILITE', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Map<String, String>> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "folder", defaultValue = "DOSSIERS_PIECES") String folder) {

        DossierStockage dossier = dossier(folder);
        ControleFichier.verifierDocument(file);
        String publicId = storageService.uploadFile(file, dossier);

        // La référence EST un jeton de document : l'inscription le résout en clé, et l'aperçu
        // le sert par notre backend. Le navigateur ne voit jamais le public_id.
        String reference = jetons.delivrer(publicId, DocumentLinkService.MINUTES_PAR_DEFAUT, null);
        return ResponseEntity.ok(Map.of(
                "reference", reference,
                "previewUrl", "/api/v1/documents/file/" + reference,
                "message", "Fichier déposé."));
    }

    private static DossierStockage dossier(String valeur) {
        // Ancienne valeur envoyée par l'écran d'inscription avant ce correctif.
        if ("docs/enrollments".equals(valeur)) {
            return DossierStockage.DOSSIERS_PIECES;
        }
        try {
            DossierStockage dossier = DossierStockage.valueOf(valeur);
            if (DOSSIERS_AUTORISES.contains(dossier)) {
                return dossier;
            }
        } catch (IllegalArgumentException inconnu) {
            // Même réponse qu'un dossier connu mais fermé : on ne dit pas ce qui existe.
        }
        throw new IllegalArgumentException("Dossier de dépôt non autorisé.");
    }
}

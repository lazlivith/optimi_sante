package com.optimisante.backend.common.storage;

import org.springframework.web.multipart.MultipartFile;

import java.util.Set;

/**
 * Contrôle d'un document déposé par un utilisateur, avant tout envoi au stockage.
 *
 * <p>Un seul endroit pour la règle — PDF, JPG ou PNG, 10 Mo au plus — et pour ses messages :
 * deux écrans qui refusent le même fichier doivent dire la même chose.</p>
 */
public final class ControleFichier {

    public static final long TAILLE_MAX_OCTETS = 10L * 1024 * 1024;
    private static final Set<String> FORMATS_ACCEPTES = Set.of("application/pdf", "image/jpeg", "image/png");

    private ControleFichier() {
    }

    /** @throws IllegalArgumentException avec un message à montrer tel quel à l'utilisateur */
    public static void verifierDocument(MultipartFile fichier) {
        if (fichier == null || fichier.isEmpty()) {
            throw new IllegalArgumentException("Aucun fichier reçu.");
        }
        if (fichier.getSize() > TAILLE_MAX_OCTETS) {
            throw new IllegalArgumentException("Le fichier dépasse 10 Mo.");
        }
        if (fichier.getContentType() == null || !FORMATS_ACCEPTES.contains(fichier.getContentType())) {
            throw new IllegalArgumentException("Format non accepté : déposez un PDF, un JPG ou un PNG.");
        }
    }
}

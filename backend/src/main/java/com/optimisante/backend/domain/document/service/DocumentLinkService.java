package com.optimisante.backend.domain.document.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Fabrique les adresses de téléchargement remises au navigateur.
 *
 * <p>C'est le seul endroit du projet qui produit un lien vers un document. Les services métier
 * l'appellent une fois qu'ils ont vérifié le droit d'accès — la vérification reste chez eux,
 * puisqu'eux seuls savent si ce médecin peut voir cette convention.</p>
 *
 * <p>L'adresse rendue pointe vers notre propre API. <b>Aucune URL de stockage ne franchit plus
 * la frontière du serveur</b> : c'était par là que les reçus se lisaient sans être connecté.</p>
 */
@Service
@RequiredArgsConstructor
public class DocumentLinkService {

    private final DocumentAccessTokenService jetons;

    /** Durée de validité par défaut : celle que les appelants utilisaient déjà. */
    public static final int MINUTES_PAR_DEFAUT = 60;

    public String lienDeTelechargement(String publicId) {
        return lienDeTelechargement(publicId, MINUTES_PAR_DEFAUT, null);
    }

    public String lienDeTelechargement(String publicId, int minutes) {
        return lienDeTelechargement(publicId, minutes, null);
    }

    /**
     * @param libelle nom lisible du fichier, sans extension — « Reçu OPT-20260829-9CEF » plutôt
     *                qu'un identifiant. Il n'a aucun effet sur le fichier servi : il ne décide que
     *                du nom proposé à l'enregistrement.
     */
    public String lienDeTelechargement(String publicId, int minutes, String libelle) {
        if (publicId == null || publicId.isBlank()) {
            throw new IllegalArgumentException("Aucun document à publier : clé de stockage vide.");
        }
        return "/api/v1/documents/file/" + jetons.delivrer(publicId, minutes, libelle);
    }
}

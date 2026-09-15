package com.optimisante.backend.domain.training.entity;

/**
 * Émetteur d'un document officiel, tel que le lit le médecin.
 *
 * ⚠️ Toute valeur ajoutée ici doit l'être simultanément dans {@code eod_issuer_check} (V57).
 */
public enum OfficialDocumentIssuer {
    /** L'établissement d'accueil, via Optimi Santé : le document a été vérifié avant publication. */
    PARTNER,
    OPTIMI
}

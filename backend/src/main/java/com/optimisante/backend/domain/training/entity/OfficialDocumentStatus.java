package com.optimisante.backend.domain.training.entity;

/**
 * Circuit de vérification d'un document officiel.
 *
 * ⚠️ Toute valeur ajoutée ici doit l'être simultanément dans {@code eod_status_check} (V57).
 */
public enum OfficialDocumentStatus {
    /** Déposé par le CHU, pas encore vérifié : le médecin ne le voit pas. */
    PENDING_REVIEW,
    /** Vérifié par Optimi Santé (ou déposé par elle) : il rejoint le coffre-fort du médecin. */
    PUBLISHED,
    /** Refusé avec un motif : le CHU le corrige et en dépose un nouveau. */
    REJECTED
}

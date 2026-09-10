package com.optimisante.backend.domain.training.entity;

/**
 * Avancement d'un service souscrit sur un dossier.
 *
 * ⚠️ Toute valeur ajoutee ici doit l'etre simultanement dans {@code eso_status_check} (V43).
 */
public enum ServiceOptionStatus {
    /** Retenu par le medecin, pas encore regle. */
    SELECTED,
    /** Regle. Un service paye ne peut plus etre retire par le medecin. */
    PAID,
    /** Abandonne avant paiement. La ligne subsiste pour que l'historique reste lisible. */
    CANCELLED
}

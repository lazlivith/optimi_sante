package com.optimisante.backend.common.email;

/**
 * Type fonctionnel d'un email envoyé par la plateforme.
 *
 * ⚠️ Toute valeur ajoutée ici doit l'être simultanément dans la contrainte SQL
 * `email_logs_type_check` (migration V24, etendue par V44).
 */
public enum EmailType {
    /** Identifiants de connexion d'un compte nouvellement provisionné (médecin, partenaire CHU). */
    CREDENTIALS,
    /** Email de vérification déclenché manuellement depuis l'espace d'administration. */
    TEST,
    /** Notification transactionnelle poussée par le NotificationDispatcher (paiement, dossier...). */
    NOTIFICATION
}

package com.optimisante.backend.common.email;

/**
 * Type fonctionnel d'un email envoyé par la plateforme.
 *
 * ⚠️ Toute valeur ajoutée ici doit l'être simultanément dans la contrainte SQL
 * `email_logs_type_check` (migrations V24 puis V41).
 */
public enum EmailType {
    /** Identifiants de connexion d'un compte nouvellement provisionné (médecin, partenaire CHU). */
    CREDENTIALS,
    /** Email de vérification déclenché manuellement depuis l'espace d'administration. */
    TEST,
    /** Créneaux d'entretien transmis au médecin par l'administration. */
    INTERVIEW_SLOTS,
    /** Confirmation du rendez-vous, une fois le créneau retenu par le médecin. */
    INTERVIEW_CONFIRMED
}

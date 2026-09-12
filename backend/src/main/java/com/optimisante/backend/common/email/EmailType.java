package com.optimisante.backend.common.email;

/**
 * Type fonctionnel d'un email envoyé par la plateforme.
 *
 * ⚠️ Toute valeur ajoutée ici doit l'être simultanément dans la contrainte SQL
 * `email_logs_type_check`, posée en V24 puis réécrite en V41 et en V50.
 *
 * <p><b>Cette contrainte est recréée à chaque extension, jamais modifiée</b> — PostgreSQL ne
 * sait pas altérer un CHECK en place. Chaque réécriture doit donc réénumérer <i>toutes</i> les
 * valeurs, y compris celles ajoutées par d'autres travaux : en omettre une ne casse ni la
 * compilation ni le démarrage, seulement l'écriture de la trace du premier email concerné.</p>
 */
public enum EmailType {
    /** Identifiants de connexion d'un compte nouvellement provisionné (médecin, partenaire CHU). */
    CREDENTIALS,
    /** Email de vérification déclenché manuellement depuis l'espace d'administration. */
    TEST,
    /** Créneaux d'entretien transmis au médecin par l'administration. */
    INTERVIEW_SLOTS,
    /** Confirmation du rendez-vous, une fois le créneau retenu par le médecin. */
    INTERVIEW_CONFIRMED,
    /** Notification transactionnelle poussée par le NotificationDispatcher (paiement, dossier...). */
    NOTIFICATION,
    /** Confirmation d'un règlement, le reçu joint au message. */
    PAYMENT_CONFIRMATION
}

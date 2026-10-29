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
    PAYMENT_CONFIRMATION,
    /** Accusé de réception envoyé au praticien qui dépose une demande d'accompagnement. */
    ACCOMPAGNEMENT_RECU,
    /** Alerte interne : une demande d'accompagnement vient d'arriver. */
    ACCOMPAGNEMENT_ALERTE_ADMIN
    // Pas de DEMANDE_BROCHURE_ALERTE_ADMIN ici, et c'est un choix contraint.
    //
    // La table email_logs porte une contrainte CHECK qui enumere les types autorises EN BASE
    // (V24, puis V50, V55, V72). Ajouter une valeur a cet enum sans etendre la contrainte fait
    // echouer l'insertion du journal — donc, en cascade, l'envoi. Et etendre la contrainte est
    // une migration, que BR-1 s'interdit.
    //
    // L'alerte de brochure part donc sous le type generique NOTIFICATION (voir
    // EmailService.sendDemandeBrochureAlerteAdmin). BR-2 porte deja une migration : c'est la
    // qu'un type dedie sera ajoute, si on juge utile de filtrer ces alertes a part.
}

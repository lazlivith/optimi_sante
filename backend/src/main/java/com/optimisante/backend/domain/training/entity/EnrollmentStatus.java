package com.optimisante.backend.domain.training.entity;

/**
 * Cycle de vie d'une candidature, modèle d'agence intermédiaire.
 *
 * Deux cycles chaînés :
 * <ol>
 *   <li><b>Commercial</b> — OptimiSanté pré-qualifie, le partenaire décide, le médecin paie.</li>
 *   <li><b>Mobilité</b> — convention, visa, départ. Inchangé, piloté par l'admin.</li>
 * </ol>
 * {@code CONFIRMED} est la jonction entre les deux : c'est ce passage qui déclenche la
 * génération de la convention tripartite et de l'attestation d'accueil.
 *
 * ⚠️ Trois sources de vérité à garder alignées à chaque modification :
 * cet enum, la contrainte SQL {@code enrollments_status_check} (V26), et
 * {@code ENROLLMENT_STEPS} côté React. Leurs divergences sont la cause d'incident
 * la plus fréquente du projet.
 *
 * Les transitions autorisées entre ces valeurs sont définies — et imposées — par
 * {@link EnrollmentTransitions}.
 */
public enum EnrollmentStatus {

    // ---- Cycle commercial ------------------------------------------------------------
    /** Dossier reçu, en cours de vérification documentaire par OptimiSanté. */
    UNDER_OPTIMI_REVIEW,
    /** Pièces manquantes ou non conformes : la main repasse au médecin. */
    ACTION_REQUIRED,
    /** Dossier pré-qualifié et transmis au partenaire pour décision pédagogique. */
    SUBMITTED_TO_PARTNER,
    /** Le partenaire retient la candidature. */
    ACCEPTED_BY_PARTNER,
    /** En attente du règlement des frais de formation par le médecin. */
    PENDING_TUITION_FEE,
    /** Formation payée, place définitivement réservée. Jonction vers le cycle de mobilité. */
    CONFIRMED,

    // ---- Cycle de mobilité (existant) ------------------------------------------------
    CONVENTION_ISSUED,
    VISA_SUBMITTED,
    VISA_GRANTED,
    READY_TO_START,

    // ---- Sorties terminales ----------------------------------------------------------
    REJECTED,
    CANCELLED
}

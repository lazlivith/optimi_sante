package com.optimisante.backend.domain.training.entity;

/**
 * Forme que prend l'entretien de selection.
 *
 * <p>Le mode n'est pas decoratif : il determine ce que la convocation doit contenir pour etre
 * utilisable. {@code ON_SITE} exige une adresse, {@code VIDEO} un lien ; {@code PHONE} se
 * contente du numero que le CHU rappellera. La contrainte
 * {@code interview_schedules_location_check} (V41) impose cette regle en base.</p>
 *
 * ⚠️ Toute valeur ajoutee ici doit l'etre simultanement dans
 * {@code interview_schedules_mode_check}.
 */
public enum InterviewMode {
    /** Visioconference : `locationOrLink` porte le lien de connexion. */
    VIDEO,
    /** Entretien telephonique : le CHU appelle le candidat. */
    PHONE,
    /** Entretien sur place : `locationOrLink` porte l'adresse complete. */
    ON_SITE
}

package com.optimisante.backend.domain.training.entity;

/**
 * Avancement de la planification d'un entretien.
 *
 * <p>Ces quatre valeurs suivent les trois mains qui se passent le dossier : le CHU propose,
 * OptimiSante transmet, le medecin choisit. <b>Elles ne font pas partie de la machine a etats
 * du dossier</b> — {@link EnrollmentStatus} est inchange. Un entretien se planifie <i>a cote</i>
 * de la candidature, comme les demandes de pieces de la V37.</p>
 *
 * ⚠️ Toute valeur ajoutee ici doit l'etre simultanement dans
 * {@code interview_schedules_status_check} (V41).
 */
public enum InterviewScheduleStatus {
    /** Creneaux deposes par le CHU. Le medecin ne les voit pas encore. */
    PROPOSED,
    /** OptimiSante a transmis les creneaux : la main est au medecin. */
    TRANSMITTED,
    /** Le medecin a retenu un creneau. Le rendez-vous est pris. */
    CONFIRMED,
    /** Planification abandonnee. Un motif est obligatoire, et un nouvel entretien redevient possible. */
    CANCELLED
}

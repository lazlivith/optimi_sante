package com.optimisante.backend.domain.training.entity;

/**
 * Cycle de vie d'une piece reclamee au candidat.
 *
 * <p>Volontairement distinct de {@link EnrollmentStatus} : une demande de piece ne fait pas
 * avancer ni reculer le dossier. Un medecin peut deposer un acte de naissance alors que son
 * dossier est deja en CONVENTION_ISSUED, sans que rien ne bascule.</p>
 *
 * <p>⚠️ Toute valeur ajoutee ici doit l'etre simultanement dans la contrainte SQL
 * {@code enrollment_document_requests_status_check} (migration V37), et sa coherence verifiee
 * dans {@code enrollment_document_requests_coherent}.</p>
 */
public enum DocumentRequestStatus {

    /** Reclamee, rien n'a encore ete depose. */
    PENDING,

    /** Le medecin a depose une piece ; elle attend la verification de l'administration. */
    SUBMITTED,

    /** Piece verifiee et retenue pour le dossier. */
    ACCEPTED,

    /** Piece refusee, avec motif. Le medecin peut en deposer une autre. */
    REJECTED,

    /** Demande retiree par l'administration : la piece n'est plus attendue. */
    CANCELLED;

    /** Une demande est ouverte tant qu'elle appelle une action de l'un ou de l'autre. */
    public boolean estOuverte() {
        return this == PENDING || this == SUBMITTED || this == REJECTED;
    }
}

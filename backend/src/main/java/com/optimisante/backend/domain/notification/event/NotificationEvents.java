package com.optimisante.backend.domain.notification.event;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Événements de domaine publiés par les services métier via {@code ApplicationEventPublisher}
 * et consommés APRÈS COMMIT par {@code NotificationDispatcher}. Ce découplage garde la logique
 * de notification hors des services métier et la rend intrinsèquement « fail-soft » : le
 * traitement se fait dans une transaction indépendante, hors du chemin critique.
 */
public final class NotificationEvents {

    private NotificationEvents() {
    }

    /** Une commande boutique vient de passer au statut payé. */
    public record OrderPaid(UUID orderId, UUID buyerUserId, String buyerEmail,
                            String orderNumber, BigDecimal totalAmount) {
    }

    /** Le statut d'un dossier de formation (inscription CHU) a changé. */
    public record EnrollmentStatusChanged(UUID enrollmentId, UUID doctorUserId, String doctorEmail,
                                          String oldStatus, String newStatus) {
    }

    /** Un compte médecin vient d'être activé (candidature payée et validée). */
    public record DoctorAccountValidated(UUID userId, String email, String fullName) {
    }

    /** Un compte partenaire (centre de formation / CHU) vient d'être activé. */
    public record PartnerAccountValidated(UUID userId, String email, String contactName, String institutionName) {
    }

    /** Une nouvelle demande de partenariat a été déposée — à examiner par l'équipe admin. */
    public record PartnershipRequestSubmitted(String institutionName, String contactEmail) {
    }

    /** Une nouvelle candidature médecin a été déposée — visible dans le back-office admin. */
    public record DoctorApplicationSubmitted(String fullName, String specialty, String email) {
    }

    // ---------------------------------------------------------------------------------
    // Cycle de candidature tripartite — un événement par main qui change de camp.
    //
    // EnrollmentStatusChanged reste le filet générique, mais il ne peut dire au médecin
    // QUE son dossier a bougé, jamais POURQUOI. Les événements ci-dessous portent le motif
    // et le destinataire reel de chaque etape, ce que la seule paire de statuts ne permet pas.
    // ---------------------------------------------------------------------------------

    /** Un médecin vient de déposer un dossier : l'équipe OptimiSanté doit l'instruire. */
    public record EnrollmentSubmitted(UUID enrollmentId, String doctorName, String doctorEmail,
                                      String trainingTitle) {
    }

    /** Dossier pré-qualifié par OptimiSanté et transmis au CHU pour décision. */
    public record EnrollmentSubmittedToPartner(UUID enrollmentId, UUID doctorUserId, String doctorEmail,
                                               String trainingTitle, String institutionName) {
    }

    /** OptimiSanté réclame une pièce au médecin : {@code note} dit laquelle, et pourquoi. */
    public record EnrollmentActionRequired(UUID enrollmentId, UUID doctorUserId, String doctorEmail,
                                           String note) {
    }

    /** Le médecin a corrigé son dossier et l'a resoumis : il retourne dans la file admin. */
    public record EnrollmentResubmitted(UUID enrollmentId, String doctorName, String trainingTitle) {
    }

    /** Le CHU réclame une pièce complémentaire ; le dossier revient à OptimiSanté. */
    public record PartnerCorrectionRequested(UUID enrollmentId, UUID doctorUserId, String doctorName,
                                             String doctorEmail, String institutionName, String note) {
    }

    /** Décision du CHU sur une candidature : acceptée ou refusée. */
    public record PartnerDecisionMade(UUID enrollmentId, UUID doctorUserId, String doctorEmail,
                                      String institutionName, boolean accepted, String reason) {
    }
}

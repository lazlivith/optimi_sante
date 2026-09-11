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
                                               String doctorName, String trainingTitle,
                                               UUID partnerUserId, String institutionName) {
    }

    /** OptimiSanté réclame une pièce au médecin : {@code note} dit laquelle, et pourquoi. */
    public record EnrollmentActionRequired(UUID enrollmentId, UUID doctorUserId, String doctorEmail,
                                           String note) {
    }

    /** Le médecin a corrigé son dossier et l'a resoumis : il retourne dans la file admin. */
    public record EnrollmentResubmitted(UUID enrollmentId, String doctorName, String trainingTitle) {
    }

    /** Le médecin a depose ou remplace des pieces : l'equipe admin a de quoi instruire. */
    public record EnrollmentDocumentsSubmitted(UUID enrollmentId, String doctorName,
                                               String trainingTitle) {
    }

    /** Frais de formation regles : la place est definitivement acquise. */
    public record TuitionPaid(UUID enrollmentId, UUID doctorUserId, String doctorName,
                              String trainingTitle, UUID partnerUserId,
                              java.math.BigDecimal amount) {
    }

    /**
     * Une piece officielle vient d'etre emise par l'administration (convention tripartite,
     * attestation d'accueil). {@code kind} porte le libelle affichable.
     */
    public record EnrollmentDocumentIssued(UUID enrollmentId, UUID doctorUserId, String doctorEmail,
                                           String kind, UUID partnerUserId, String doctorName) {
    }

    /** Etape du parcours de mobilite franchie (convention, visa, depart). */
    public record MobilityAdvanced(UUID enrollmentId, UUID doctorUserId, String doctorEmail,
                                   String newStatus) {
    }

    /** Dossier annule par l'administration : medecin et etablissement doivent le savoir. */
    public record EnrollmentCancelled(UUID enrollmentId, UUID doctorUserId, String doctorEmail,
                                      String doctorName, UUID partnerUserId, String reason) {
    }

    /** Une formation est soumise (ou resoumise) par un CHU et attend la validation admin. */
    public record TrainingSubmittedForApproval(UUID trainingId, String title,
                                               String institutionName) {
    }

    /** Verdict de l'administration sur une formation proposee par un CHU. */
    public record TrainingApprovalDecided(UUID trainingId, UUID partnerUserId, String title,
                                          boolean approved, String reason) {
    }

    /** Demande de partenariat refusee : le candidat n'a pas de compte, seul l'e-mail le joint. */
    public record PartnershipRequestRejected(String institutionName, String contactEmail,
                                             String reason) {
    }

    /** Le CHU réclame une pièce complémentaire ; le dossier revient à OptimiSanté. */
    public record PartnerCorrectionRequested(UUID enrollmentId, UUID doctorUserId, String doctorName,
                                             String doctorEmail, String institutionName, String note) {
    }

    /** Décision du CHU sur une candidature : acceptée ou refusée. */
    public record PartnerDecisionMade(UUID enrollmentId, UUID doctorUserId, String doctorEmail,
                                      String doctorName, String institutionName,
                                      boolean accepted, String reason) {
    }
}

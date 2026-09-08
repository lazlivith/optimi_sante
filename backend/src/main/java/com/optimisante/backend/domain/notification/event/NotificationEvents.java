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
}

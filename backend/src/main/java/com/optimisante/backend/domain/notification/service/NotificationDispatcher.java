package com.optimisante.backend.domain.notification.service;

import com.optimisante.backend.common.email.EmailService;
import com.optimisante.backend.domain.identity.entity.Role;
import com.optimisante.backend.domain.notification.entity.NotificationSeverity;
import com.optimisante.backend.domain.notification.event.NotificationEvents;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Traduit les événements de domaine en notifications in-app (+ e-mail quand c'est pertinent, et
 * si l'utilisateur ne l'a pas coupé). Chaque écouteur s'exécute APRÈS COMMIT et de façon
 * asynchrone : le chemin critique n'attend jamais l'envoi. {@link NotificationService} et
 * {@link EmailService} sont « fail-soft », un échec ici reste sans conséquence.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationDispatcher {

    private final NotificationService notifications;
    private final EmailService emailService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderPaid(NotificationEvents.OrderPaid e) {
        String body = "Votre commande " + e.orderNumber() + " d'un montant de "
                + e.totalAmount() + " € est confirmée. Votre reçu est disponible dans votre espace.";
        notifications.notifyUser(e.buyerUserId(), "ORDER_PAID", NotificationSeverity.SUCCESS,
                "Paiement confirmé", body, "/my-orders", null, "ORDER_PAID:" + e.orderId());
        if (notifications.emailAllowed(e.buyerUserId(), "ORDER_PAID")) {
            emailService.sendHtml(e.buyerEmail(), "Optimi Santé — Paiement confirmé (" + e.orderNumber() + ")",
                    "Votre paiement est confirmé", "<p>" + body + "</p>");
        }
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onEnrollmentStatusChanged(NotificationEvents.EnrollmentStatusChanged e) {
        String label = humanize(e.newStatus());
        String body = "Votre dossier de formation est passé au statut « " + label + " ».";
        notifications.notifyUser(e.doctorUserId(), "ENROLLMENT_STATUS", NotificationSeverity.INFO,
                "Dossier de formation mis à jour", body, "/doctor", null,
                "ENROLLMENT_STATUS:" + e.enrollmentId());
        if (notifications.emailAllowed(e.doctorUserId(), "ENROLLMENT_STATUS")) {
            emailService.sendHtml(e.doctorEmail(), "Optimi Santé — Votre dossier de formation a évolué",
                    "Mise à jour de votre dossier",
                    "<p>" + body + "</p><p>Connectez-vous à votre espace médecin pour le détail.</p>");
        }
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDoctorAccountValidated(NotificationEvents.DoctorAccountValidated e) {
        notifications.notifyUser(e.userId(), "ACCOUNT_VALIDATED", NotificationSeverity.SUCCESS,
                "Compte médecin activé",
                "Bienvenue " + e.fullName() + " — votre espace médecin est désormais actif.",
                "/doctor", null, "ACCOUNT_VALIDATED");
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPartnerAccountValidated(NotificationEvents.PartnerAccountValidated e) {
        notifications.notifyUser(e.userId(), "ACCOUNT_VALIDATED", NotificationSeverity.SUCCESS,
                "Compte partenaire activé",
                "L'établissement " + e.institutionName() + " a désormais accès à l'espace partenaire.",
                "/partner", null, "ACCOUNT_VALIDATED");
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPartnershipRequestSubmitted(NotificationEvents.PartnershipRequestSubmitted e) {
        notifications.notifyRole(Role.ADMIN, "PARTNERSHIP_REQUEST", NotificationSeverity.WARNING,
                "Nouvelle demande de partenariat",
                e.institutionName() + " a soumis une demande de partenariat à examiner.",
                "/admin/partnership-requests", null, "PARTNERSHIP_REQUEST");
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDoctorApplicationSubmitted(NotificationEvents.DoctorApplicationSubmitted e) {
        notifications.notifyRole(Role.ADMIN, "DOCTOR_APPLICATION", NotificationSeverity.INFO,
                "Nouvelle candidature médecin",
                e.fullName() + (e.specialty() == null ? "" : " (" + e.specialty() + ")") + " a déposé une candidature.",
                "/admin/enrollments", null, "DOCTOR_APPLICATION");
    }

    private static String humanize(String status) {
        if (status == null) {
            return "";
        }
        return status.replace('_', ' ').toLowerCase();
    }
}

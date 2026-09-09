package com.optimisante.backend.domain.notification.service;

import com.optimisante.backend.common.email.EmailService;
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
        notifications.notifyAdmins("PARTNERSHIP_REQUEST", NotificationSeverity.WARNING,
                "Nouvelle demande de partenariat",
                e.institutionName() + " a soumis une demande de partenariat à examiner.",
                "/admin/partnership-requests", null, "PARTNERSHIP_REQUEST");
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDoctorApplicationSubmitted(NotificationEvents.DoctorApplicationSubmitted e) {
        notifications.notifyAdmins("DOCTOR_APPLICATION", NotificationSeverity.INFO,
                "Nouvelle candidature médecin",
                e.fullName() + (e.specialty() == null ? "" : " (" + e.specialty() + ")") + " a déposé une candidature.",
                "/admin/enrollments", null, "DOCTOR_APPLICATION");
    }

    // =====================================================================================
    // Cycle de candidature tripartite
    // =====================================================================================

    /** Depot d'un dossier : l'equipe admin est prevenue dans l'app ET par e-mail. */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onEnrollmentSubmitted(NotificationEvents.EnrollmentSubmitted e) {
        String body = e.doctorName() + " a déposé un dossier pour « " + e.trainingTitle()
                + " ». Les pièces sont à vérifier avant transmission au CHU.";
        notifications.notifyAdmins("ENROLLMENT_SUBMITTED", NotificationSeverity.WARNING,
                "Nouveau dossier de candidature", body,
                "/admin/enrollments/" + e.enrollmentId(), null,
                "ENROLLMENT_SUBMITTED:" + e.enrollmentId());
        emailAdmins("Optimi Santé — Nouveau dossier de candidature",
                "Nouveau dossier à instruire",
                "<p>" + body + "</p><p>Adresse du candidat : " + e.doctorEmail() + "</p>");
    }

    /** Dossier transmis au CHU : le medecin apprend que son dossier a passe la revue interne. */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onEnrollmentSubmittedToPartner(NotificationEvents.EnrollmentSubmittedToPartner e) {
        String body = "Votre dossier pour « " + e.trainingTitle() + " » a été vérifié par notre équipe "
                + "et transmis à " + e.institutionName() + ", qui statuera sur votre admission. "
                + "Votre dossier est en cours de traitement.";
        notifications.notifyUser(e.doctorUserId(), "ENROLLMENT_STATUS", NotificationSeverity.SUCCESS,
                "Dossier transmis à l'établissement", body,
                "/doctor/enrollments/" + e.enrollmentId(), null,
                "ENROLLMENT_TO_PARTNER:" + e.enrollmentId());
        if (notifications.emailAllowed(e.doctorUserId(), "ENROLLMENT_STATUS")) {
            emailService.sendHtml(e.doctorEmail(),
                    "Optimi Santé — Votre dossier est transmis à l'établissement",
                    "Votre dossier est en cours de traitement", "<p>" + body + "</p>");
        }
    }

    /**
     * Piece manquante ou non conforme. Le motif saisi par l'administrateur est repris tel quel
     * dans la notification et l'e-mail : sans lui, le medecin sait seulement que quelque chose
     * ne va pas, sans savoir quoi corriger.
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onEnrollmentActionRequired(NotificationEvents.EnrollmentActionRequired e) {
        String body = "Votre dossier nécessite une correction avant de pouvoir être transmis : "
                + e.note();
        notifications.notifyUser(e.doctorUserId(), "ENROLLMENT_STATUS", NotificationSeverity.WARNING,
                "Pièce à corriger dans votre dossier", body,
                "/doctor/enrollments/" + e.enrollmentId(), null,
                "ENROLLMENT_ACTION:" + e.enrollmentId());
        if (notifications.emailAllowed(e.doctorUserId(), "ENROLLMENT_STATUS")) {
            emailService.sendHtml(e.doctorEmail(),
                    "Optimi Santé — Une pièce de votre dossier est à corriger",
                    "Votre dossier demande une correction",
                    "<p>" + body + "</p><p>Déposez la pièce demandée depuis votre espace médecin, "
                            + "puis resoumettez votre dossier.</p>");
        }
    }

    /** Le medecin a corrige : le dossier revient dans la file d'instruction admin. */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onEnrollmentResubmitted(NotificationEvents.EnrollmentResubmitted e) {
        String body = e.doctorName() + " a corrigé son dossier pour « " + e.trainingTitle()
                + " ». Il est de nouveau en attente de vérification.";
        notifications.notifyAdmins("ENROLLMENT_RESUBMITTED", NotificationSeverity.INFO,
                "Dossier corrigé et resoumis", body,
                "/admin/enrollments/" + e.enrollmentId(), null,
                "ENROLLMENT_RESUBMITTED:" + e.enrollmentId());
    }

    /** Le CHU reclame une piece : c'est OptimiSante qui relaie, jamais le CHU en direct. */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPartnerCorrectionRequested(NotificationEvents.PartnerCorrectionRequested e) {
        String body = e.institutionName() + " réclame une pièce complémentaire sur le dossier de "
                + e.doctorName() + " : " + e.note();
        notifications.notifyAdmins("PARTNER_CORRECTION", NotificationSeverity.WARNING,
                "L'établissement demande une pièce", body,
                "/admin/enrollments/" + e.enrollmentId(), null,
                "PARTNER_CORRECTION:" + e.enrollmentId());
        emailAdmins("Optimi Santé — L'établissement réclame une pièce",
                "Demande de pièce complémentaire", "<p>" + body + "</p>");

        // Le medecin est aussi prevenu : c'est lui qui doit deposer la piece, et son dossier
        // est deja repasse en ACTION_REQUIRED. Le message reste emis par OptimiSante — le
        // CHU ne s'adresse jamais directement au candidat.
        String doctorBody = "Votre dossier nécessite une pièce complémentaire : " + e.note();
        notifications.notifyUser(e.doctorUserId(), "ENROLLMENT_STATUS", NotificationSeverity.WARNING,
                "Pièce à fournir dans votre dossier", doctorBody,
                "/doctor/enrollments/" + e.enrollmentId(), null,
                "ENROLLMENT_ACTION:" + e.enrollmentId());
        if (notifications.emailAllowed(e.doctorUserId(), "ENROLLMENT_STATUS")) {
            emailService.sendHtml(e.doctorEmail(),
                    "Optimi Santé — Une pièce complémentaire est demandée",
                    "Votre dossier demande une pièce",
                    "<p>" + doctorBody + "</p><p>Déposez-la depuis votre espace médecin, "
                            + "puis resoumettez votre dossier.</p>");
        }
    }

    /** Decision d'admission du CHU, relayee au medecin. */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPartnerDecisionMade(NotificationEvents.PartnerDecisionMade e) {
        String body = e.accepted()
                ? "Bonne nouvelle : " + e.institutionName() + " a retenu votre candidature. "
                  + "Il vous reste à régler les frais de formation pour confirmer votre place."
                : "Votre candidature n'a pas été retenue par " + e.institutionName()
                  + (e.reason() == null || e.reason().isBlank() ? "." : " : " + e.reason());
        notifications.notifyUser(e.doctorUserId(), "ENROLLMENT_STATUS",
                e.accepted() ? NotificationSeverity.SUCCESS : NotificationSeverity.WARNING,
                e.accepted() ? "Candidature acceptée" : "Candidature refusée", body,
                "/doctor/enrollments/" + e.enrollmentId(), null,
                "PARTNER_DECISION:" + e.enrollmentId());
        if (notifications.emailAllowed(e.doctorUserId(), "ENROLLMENT_STATUS")) {
            emailService.sendHtml(e.doctorEmail(),
                    e.accepted()
                            ? "Optimi Santé — Votre candidature est acceptée"
                            : "Optimi Santé — Réponse à votre candidature",
                    e.accepted() ? "Candidature acceptée" : "Candidature refusée",
                    "<p>" + body + "</p>");
        }
    }

    /** Envoi groupe a l'equipe d'administration, best-effort comme tout le reste du dispatcher. */
    private void emailAdmins(String subject, String heading, String innerHtml) {
        for (String address : notifications.adminEmails()) {
            emailService.sendHtml(address, subject, heading, innerHtml);
        }
    }

    /**
     * Libelle lisible d'un statut. Le repli mecanique (tirets bas remplaces par des espaces)
     * produisait « under optimi review » dans les notifications recues par les medecins :
     * un identifiant technique, en anglais, dans un message destine au candidat.
     */
    private static String humanize(String status) {
        if (status == null) {
            return "";
        }
        return switch (status) {
            case "UNDER_OPTIMI_REVIEW"  -> "en cours de vérification";
            case "ACTION_REQUIRED"      -> "en attente de correction";
            case "SUBMITTED_TO_PARTNER" -> "transmis à l'établissement";
            case "ACCEPTED_BY_PARTNER"  -> "accepté par l'établissement";
            case "PENDING_TUITION_FEE"  -> "en attente du paiement des frais";
            case "CONFIRMED"            -> "inscription confirmée";
            case "CONVENTION_ISSUED"    -> "convention émise";
            case "VISA_SUBMITTED"       -> "demande de visa déposée";
            case "VISA_GRANTED"         -> "visa obtenu";
            case "READY_TO_START"       -> "prêt au départ";
            case "REJECTED"             -> "refusé";
            case "CANCELLED"            -> "annulé";
            default -> status.replace('_', ' ').toLowerCase();
        };
    }
}

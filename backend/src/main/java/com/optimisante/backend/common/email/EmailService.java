package com.optimisante.backend.common.email;

import com.optimisante.backend.domain.identity.entity.User;

import java.util.UUID;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;
    private final EmailLogWriter emailLogWriter;

    @Value("${app.mail.from}")
    private String fromAddress;

    @Value("${app.mail.frontend-base-url}")
    private String frontendBaseUrl;

    /**
     * Envoie les identifiants de connexion à un compte nouvellement créé (médecin validé,
     * partenaire CHU validé). Échec non bloquant : si l'envoi échoue (SMTP indisponible,
     * identifiants invalides...), l'erreur est loguée ET tracée dans `email_logs` mais ne
     * remonte pas — la validation admin reste effective, le compte existe même si l'email
     * n'est pas parti. L'administrateur peut alors le renvoyer depuis l'espace « Emails ».
     */
    public void sendCredentialsEmail(String toEmail, String recipientName, String temporaryPassword, String roleLabel) {
        // Cast explicite : `null` seul serait ambigu entre les surcharges User et UUID.
        sendCredentialsEmail(toEmail, recipientName, temporaryPassword, roleLabel, (UUID) null);
    }

    /** Variante traçant le compte destinataire, ce qui rend le renvoi possible depuis l'admin. */
    public void sendCredentialsEmail(String toEmail, String recipientName, String temporaryPassword,
                                     String roleLabel, User recipientUser) {
        sendCredentialsEmail(toEmail, recipientName, temporaryPassword, roleLabel,
                recipientUser != null ? recipientUser.getId() : null);
    }

    /** Variante par identifiant : utilisée lors d'un renvoi depuis l'administration. */
    public void sendCredentialsEmail(String toEmail, String recipientName, String temporaryPassword,
                                     String roleLabel, UUID recipientUserId) {
        String subject = "Optimi Santé — Vos identifiants de connexion";
        String loginUrl = frontendBaseUrl + "/login";
        String html = """
                <div style="font-family: Arial, sans-serif; max-width: 560px; margin: 0 auto; color: #1a2e29;">
                    <div style="background-color: #154D44; padding: 24px; border-radius: 12px 12px 0 0;">
                        <h1 style="color: #ffffff; margin: 0; font-size: 20px;">Optimi Santé</h1>
                    </div>
                    <div style="border: 1px solid #E2EBE5; border-top: none; padding: 32px; border-radius: 0 0 12px 12px;">
                        <p>Bonjour %s,</p>
                        <p>Votre dossier a été validé par notre équipe. Votre espace <strong>%s</strong> est maintenant actif sur la plateforme Optimi Santé.</p>
                        <div style="background-color: #F6F7F5; border-radius: 8px; padding: 16px; margin: 24px 0;">
                            <p style="margin: 4px 0;"><strong>Email :</strong> %s</p>
                            <p style="margin: 4px 0;"><strong>Mot de passe provisoire :</strong> %s</p>
                        </div>
                        <p style="color: #D98A3C; font-weight: bold;">Merci de changer ce mot de passe dès votre première connexion.</p>
                        <a href="%s" style="display: inline-block; background-color: #154D44; color: #ffffff; text-decoration: none; padding: 12px 24px; border-radius: 8px; margin-top: 16px;">Me connecter</a>
                        <p style="margin-top: 32px; font-size: 12px; color: #8a9490;">Optimi Santé — Faciliter la mobilité en formation pour les médecins d'Afrique</p>
                    </div>
                </div>
                """.formatted(recipientName, roleLabel, toEmail, temporaryPassword, loginUrl);

        send(toEmail, subject, html, EmailType.CREDENTIALS, recipientUserId);
    }

    /**
     * Envoi d'un e-mail transactionnel générique : {@code innerHtml} est le corps du message,
     * inséré dans la charte visuelle commune (en-tête vert, pied de page). Échec non bloquant,
     * comme {@link #sendCredentialsEmail} — utilisé par le {@code NotificationDispatcher}.
     */
    public void sendHtml(String to, String subject, String heading, String innerHtml) {
        if (to == null || to.isBlank()) {
            return;
        }
        send(to, subject, wrap(heading, innerHtml), EmailType.NOTIFICATION, null);
    }

    private String wrap(String heading, String innerHtml) {
        return """
                <div style="font-family: Arial, sans-serif; max-width: 560px; margin: 0 auto; color: #1a2e29;">
                    <div style="background-color: #154D44; padding: 24px; border-radius: 12px 12px 0 0;">
                        <h1 style="color: #ffffff; margin: 0; font-size: 20px;">Optimi Santé</h1>
                    </div>
                    <div style="border: 1px solid #E2EBE5; border-top: none; padding: 32px; border-radius: 0 0 12px 12px;">
                        <h2 style="margin: 0 0 16px; font-size: 16px; color: #154D44;">%s</h2>
                        %s
                        <p style="margin-top: 32px; font-size: 12px; color: #8a9490;">Optimi Santé — Faciliter la mobilité en formation pour les médecins d'Afrique</p>
                    </div>
                </div>
                """.formatted(heading, innerHtml);
    }

    /**
     * Email de vérification déclenché manuellement depuis l'espace d'administration :
     * permet de contrôler la configuration SMTP et le rendu réel dans une vraie boîte mail.
     * Contrairement aux autres envois, celui-ci propage l'échec afin que l'administrateur
     * voie immédiatement l'erreur exacte à l'écran.
     */
    public void sendTestEmail(String toEmail) {
        String subject = "Optimi Santé — Email de test";
        String html = """
                <div style="font-family: Arial, sans-serif; max-width: 560px; margin: 0 auto; color: #1a2e29;">
                    <div style="background-color: #154D44; padding: 24px; border-radius: 12px 12px 0 0;">
                        <h1 style="color: #ffffff; margin: 0; font-size: 20px;">Optimi Santé</h1>
                    </div>
                    <div style="border: 1px solid #E2EBE5; border-top: none; padding: 32px; border-radius: 0 0 12px 12px;">
                        <p>Cet email confirme que l'envoi depuis la plateforme Optimi Santé fonctionne correctement.</p>
                        <p style="margin-top: 24px;">Si vous le lisez dans une vraie boîte de réception, la configuration
                        d'envoi (domaine, SPF/DKIM) est opérationnelle.</p>
                        <p style="margin-top: 32px; font-size: 12px; color: #8a9490;">Optimi Santé — Faciliter la mobilité en formation pour les médecins d'Afrique</p>
                    </div>
                </div>
                """;

        sendOrThrow(toEmail, subject, html, EmailType.TEST);
    }

    /**
     * Prévient le médecin que des créneaux d'entretien lui sont proposés.
     *
     * <p>Le message est volontairement court : il n'énumère pas les créneaux. Une liste de
     * dates dans un email vieillit mal — le médecin la lirait après qu'un créneau a été retenu
     * ou l'entretien annulé, et croirait des informations périmées. L'email amène sur le
     * dossier, où l'état affiché est toujours le vrai.</p>
     *
     * <p>Échec non bloquant, comme l'envoi des identifiants : si le SMTP est indisponible, les
     * créneaux restent transmis et visibles dans l'espace du médecin.</p>
     */
    public void sendInterviewSlotsEmail(String toEmail, String doctorName, String trainingTitle,
                                        String institutionName, int nombreCreneaux,
                                        UUID recipientUserId) {
        String subject = "Optimi Santé — Entretien en visioconférence : créneaux à choisir";
        String dossierUrl = frontendBaseUrl + "/doctor/enrollments";
        String html = """
                <div style="font-family: Arial, sans-serif; max-width: 560px; margin: 0 auto; color: #1a2e29;">
                    <div style="background-color: #154D44; padding: 24px; border-radius: 12px 12px 0 0;">
                        <h1 style="color: #ffffff; margin: 0; font-size: 20px;">Optimi Santé</h1>
                    </div>
                    <div style="border: 1px solid #E2EBE5; border-top: none; padding: 32px; border-radius: 0 0 12px 12px;">
                        <p>Bonjour %s,</p>
                        <p><strong>%s</strong> vous propose un entretien de sélection pour la formation
                        <strong>%s</strong>. Il se tiendra <strong>en visioconférence</strong> : aucun
                        déplacement n'est nécessaire.</p>
                        <p><strong>%d créneaux</strong> vous sont proposés. Choisissez celui qui vous convient
                        depuis votre dossier : votre choix vaut confirmation du rendez-vous. Vous recevrez
                        alors le lien de connexion, et votre convocation visio sera déposée dans vos
                        documents.</p>
                        <a href="%s" style="display: inline-block; background-color: #154D44; color: #ffffff; text-decoration: none; padding: 12px 24px; border-radius: 8px; margin-top: 16px;">Choisir mon créneau</a>
                        <p style="margin-top: 32px; font-size: 12px; color: #8a9490;">Optimi Santé — Faciliter la mobilité en formation pour les médecins d'Afrique</p>
                    </div>
                </div>
                """.formatted(doctorName, institutionName, trainingTitle, nombreCreneaux, dossierUrl);

        send(toEmail, subject, html, EmailType.INTERVIEW_SLOTS, recipientUserId);
    }

    /**
     * Confirme le rendez-vous retenu, avec le lien de connexion.
     *
     * <p>Celui-ci porte la date <b>et le lien</b> : l'entretien se tient en visioconférence, et
     * c'est dans sa boîte mail que le destinataire ira chercher par où se connecter, le jour
     * venu. Un message qui renverrait vers la plateforme obligerait à s'y reconnecter cinq
     * minutes avant l'entretien — précisément le moment où on ne veut pas chercher.</p>
     *
     * <p>Le même message sert au médecin et à l'établissement : ils rejoignent la même réunion.</p>
     */
    public void sendInterviewConfirmedEmail(String toEmail, String destinataire, String doctorName,
                                            String trainingTitle, String creneau,
                                            String meetingLink, UUID recipientUserId) {
        String subject = "Optimi Santé — Entretien en visioconférence confirmé : " + creneau;
        String html = """
                <div style="font-family: Arial, sans-serif; max-width: 560px; margin: 0 auto; color: #1a2e29;">
                    <div style="background-color: #154D44; padding: 24px; border-radius: 12px 12px 0 0;">
                        <h1 style="color: #ffffff; margin: 0; font-size: 20px;">Optimi Santé</h1>
                    </div>
                    <div style="border: 1px solid #E2EBE5; border-top: none; padding: 32px; border-radius: 0 0 12px 12px;">
                        <p>Bonjour %s,</p>
                        <p>L'entretien de sélection de <strong>Dr. %s</strong> pour la formation
                        <strong>%s</strong> est confirmé. Il se tiendra <strong>en visioconférence</strong> :
                        aucun déplacement n'est nécessaire.</p>
                        <div style="background-color: #F6F7F5; border-radius: 8px; padding: 16px; margin: 24px 0;">
                            <p style="margin: 4px 0;"><strong>Date :</strong> %s</p>
                            <p style="margin: 12px 0 4px 0;"><strong>Lien de la réunion en ligne :</strong></p>
                            <p style="margin: 0; word-break: break-all;"><a href="%s" style="color: #154D44;">%s</a></p>
                        </div>
                        <a href="%s" style="display: inline-block; background-color: #154D44; color: #ffffff; text-decoration: none; padding: 12px 24px; border-radius: 8px;">Rejoindre la visioconférence</a>
                        <p style="margin-top: 20px; font-size: 13px; color: #5b6b66;">Connectez-vous quelques minutes
                        à l'avance et vérifiez votre micro et votre caméra. La convocation est disponible dans les
                        documents du dossier.</p>
                        <p style="margin-top: 32px; font-size: 12px; color: #8a9490;">Optimi Santé — Faciliter la mobilité en formation pour les médecins d'Afrique</p>
                    </div>
                </div>
                """.formatted(destinataire, doctorName, trainingTitle, creneau,
                              meetingLink, meetingLink, meetingLink);

        send(toEmail, subject, html, EmailType.INTERVIEW_CONFIRMED, recipientUserId);
    }

    /**
     * Délai avant la seconde tentative, quand le relais a refusé pour cadence excessive.
     *
     * <p>Un peu plus d'une seconde : les relais qui limitent le débit raisonnent à la seconde.</p>
     */
    private static final long PAUSE_AVANT_SECONDE_TENTATIVE_MS = 1200L;

    /**
     * Envoi non bloquant : trace le résultat, n'interrompt jamais le flux métier appelant.
     *
     * <p><b>Une seconde tentative est faite lorsque le refus porte sur la cadence.</b> Certains
     * gestes de la plateforme envoient deux messages coup sur coup — la confirmation d'un
     * entretien prévient le médecin <i>et</i> l'établissement. Les relais qui limitent le débit
     * refusent alors le second de façon systématique, et c'est toujours le même destinataire
     * qui est perdu : l'établissement, celui-là même qui recevra le candidat. Ce n'est donc pas
     * un aléa mais un angle mort reproductible, qu'une pause d'une seconde suffit à lever.</p>
     *
     * <p>Le nouvel essai ne concerne que ce refus-là. Une adresse invalide ou un mot de passe
     * SMTP erroné échoueront pareillement la seconde fois : réessayer ne ferait qu'ajouter de
     * la latence à une erreur déjà certaine.</p>
     */
    private void send(String to, String subject, String html, EmailType type, UUID recipientUserId) {
        try {
            deliver(to, subject, html);
            log.info("Email envoyé à {}", to);
            trace(to, subject, type, EmailStatus.SENT, null, recipientUserId);
            return;
        } catch (Exception premiereErreur) {
            if (!refusDeCadence(premiereErreur)) {
                log.error("Échec de l'envoi d'email à {} : {}", to, premiereErreur.getMessage());
                trace(to, subject, type, EmailStatus.FAILED, premiereErreur.getMessage(), recipientUserId);
                return;
            }
            log.warn("Cadence refusée par le relais pour {} : nouvelle tentative dans {} ms",
                    to, PAUSE_AVANT_SECONDE_TENTATIVE_MS);
        }

        try {
            Thread.sleep(PAUSE_AVANT_SECONDE_TENTATIVE_MS);
            deliver(to, subject, html);
            log.info("Email envoyé à {} à la seconde tentative", to);
            trace(to, subject, type, EmailStatus.SENT, null, recipientUserId);
        } catch (InterruptedException interruption) {
            // Restaurer le drapeau : l'avaler priverait l'appelant de l'information.
            Thread.currentThread().interrupt();
            log.error("Attente interrompue avant la seconde tentative vers {}", to);
            trace(to, subject, type, EmailStatus.FAILED, "Attente interrompue", recipientUserId);
        } catch (Exception secondeErreur) {
            log.error("Échec de l'envoi d'email à {} après deux tentatives : {}",
                    to, secondeErreur.getMessage());
            trace(to, subject, type, EmailStatus.FAILED, secondeErreur.getMessage(), recipientUserId);
        }
    }

    /**
     * Le relais a-t-il refusé pour cause de cadence ?
     *
     * <p>Reconnu sur le texte du message et non sur le code SMTP : les relais utilisent pour ce
     * refus des codes contradictoires — un 421 ou 450 transitoire chez les uns, un 550
     * définitif chez d'autres. Se fier au code laisserait passer la moitié des cas.</p>
     */
    private boolean refusDeCadence(Exception e) {
        String message = e.getMessage();
        if (message == null) {
            return false;
        }
        String m = message.toLowerCase(java.util.Locale.ROOT);
        return m.contains("too many emails")
                || m.contains("rate limit")
                || m.contains("too many messages")
                || m.contains("throttl");
    }

    /** Envoi bloquant : trace le résultat puis propage l'erreur à l'appelant. */
    private void sendOrThrow(String to, String subject, String html, EmailType type) {
        try {
            deliver(to, subject, html);
            log.info("Email de test envoyé à {}", to);
            trace(to, subject, type, EmailStatus.SENT, null, null);
        } catch (Exception e) {
            log.error("Échec de l'email de test à {} : {}", to, e.getMessage());
            trace(to, subject, type, EmailStatus.FAILED, e.getMessage(), null);
            throw new IllegalStateException("Échec de l'envoi : " + e.getMessage());
        }
    }

    private void deliver(String to, String subject, String html) throws Exception {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
        helper.setFrom(fromAddress);
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(html, true);
        mailSender.send(message);
    }

    /**
     * Délègue l'écriture de la trace à {@link EmailLogWriter}, qui la commite dans une
     * transaction indépendante — indispensable pour qu'une trace d'ÉCHEC survive au
     * rollback provoqué par l'erreur d'envoi elle-même (voir EmailLogWriter).
     */
    private void trace(String to, String subject, EmailType type, EmailStatus status,
                       String errorMessage, UUID recipientUserId) {
        emailLogWriter.record(to, subject, type, status, errorMessage, recipientUserId);
    }
}

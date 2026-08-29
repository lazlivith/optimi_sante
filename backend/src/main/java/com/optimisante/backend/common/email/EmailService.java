package com.optimisante.backend.common.email;

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

    @Value("${app.mail.from}")
    private String fromAddress;

    @Value("${app.mail.frontend-base-url}")
    private String frontendBaseUrl;

    /**
     * Envoie les identifiants de connexion à un compte nouvellement créé (médecin validé,
     * partenaire CHU validé). Échec non bloquant : si l'envoi échoue (Mailtrap non configuré,
     * etc.), l'erreur est loguée mais ne remonte pas — la validation admin reste effective,
     * le compte existe même si l'email n'est pas parti.
     */
    public void sendCredentialsEmail(String toEmail, String recipientName, String temporaryPassword, String roleLabel) {
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

        send(toEmail, subject, html);
    }

    private void send(String to, String subject, String html) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(message);
            log.info("Email envoyé à {}", to);
        } catch (Exception e) {
            log.error("Échec de l'envoi d'email à {} (Mailtrap non configuré ou identifiants invalides ?) : {}", to, e.getMessage());
        }
    }
}

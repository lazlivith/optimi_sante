package com.optimisante.backend.common.email;

import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Ce qu'un nom saisi par un utilisateur devient dans le corps d'un message.
 *
 * <p>Le cas qui compte n'est pas celui d'un destinataire qui s'injecte dans son propre courriel,
 * mais celui du nom d'un médecin repris dans le message envoyé à l'établissement.</p>
 */
@DisplayName("Échappement des textes dans les e-mails")
class EmailServiceEchappementTest {

    private static final String NOM_PIEGE = "Dr. <script>alert(1)</script> Dupont";

    private JavaMailSender mailSender;
    private EmailService emailService;

    @BeforeEach
    void preparer() {
        mailSender = mock(JavaMailSender.class);
        when(mailSender.createMimeMessage()).thenAnswer(i -> new MimeMessage((jakarta.mail.Session) null));
        emailService = new EmailService(mailSender, mock(EmailLogWriter.class));
        ReflectionTestUtils.setField(emailService, "fromAddress", "no-reply@optimisante.fr");
        ReflectionTestUtils.setField(emailService, "frontendBaseUrl", "https://app.optimisante.fr");
    }

    private String corpsEnvoye() throws Exception {
        ArgumentCaptor<MimeMessage> capture = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(capture.capture());
        return capture.getValue().getContent().toString();
    }

    @Test
    @DisplayName("le nom du médecin arrive en texte chez l'établissement")
    void nomEchappeDansLaConfirmationDEntretien() throws Exception {
        emailService.sendInterviewConfirmedEmail("chu@exemple.fr", "CHU de Rennes", NOM_PIEGE,
                "Chirurgie & anesthésie", "12/10/2026 à 14 h",
                "https://visio.exemple.fr/r/abc", UUID.randomUUID());

        String corps = corpsEnvoye();
        assertThat(corps).doesNotContain("<script>");
        assertThat(corps).contains("&lt;script&gt;alert(1)&lt;/script&gt;");
        // L'esperluette d'un intitulé légitime est encodée, donc affichée telle quelle.
        assertThat(corps).contains("Chirurgie &amp; anesth");
        // La mise en page de la plateforme, elle, reste du vrai HTML.
        assertThat(corps).contains("<h1 style=");
    }

    @Test
    @DisplayName("le nom et l'intitulé de rôle sont échappés dans l'envoi d'identifiants")
    void nomEchappeDansLesIdentifiants() throws Exception {
        emailService.sendCredentialsEmail("medecin@exemple.fr", NOM_PIEGE, "Ab3Cd4Ef5Gh6",
                "Médecin", (UUID) null);

        String corps = corpsEnvoye();
        assertThat(corps).doesNotContain("<script>");
        assertThat(corps).contains("&lt;script&gt;");
        // Un mot de passe provisoire n'emploie que lettres et chiffres : il doit rester lisible tel quel.
        assertThat(corps).contains("Ab3Cd4Ef5Gh6");
    }

    @Test
    @DisplayName("le titre d'un message générique est échappé, son corps reste du HTML")
    void titreEchappeMaisCorpsLibre() throws Exception {
        emailService.sendHtml("medecin@exemple.fr", "Sujet", "<b>Titre</b>", "<p>Corps <strong>gras</strong></p>");

        String corps = corpsEnvoye();
        assertThat(corps).contains("&lt;b&gt;Titre&lt;/b&gt;");
        assertThat(corps).contains("<p>Corps <strong>gras</strong></p>");
    }

    @Test
    @DisplayName("une valeur absente ne fait pas apparaître « null » dans le message")
    void valeurAbsente() throws Exception {
        emailService.sendInterviewSlotsEmail("medecin@exemple.fr", null, "Formation", "CHU", 3,
                UUID.randomUUID());

        assertThat(corpsEnvoye()).doesNotContain("null");
    }

    @Test
    @DisplayName("aucune exception ne remonte si l'envoi échoue")
    void echecNonBloquant() {
        when(mailSender.createMimeMessage()).thenThrow(new IllegalStateException("SMTP indisponible"));
        emailService.sendInterviewConfirmedEmail("chu@exemple.fr", "CHU", NOM_PIEGE, "Formation",
                "12/10/2026", "https://visio.exemple.fr/r/abc", UUID.randomUUID());
        verify(mailSender, org.mockito.Mockito.never()).send(any(MimeMessage.class));
    }
}

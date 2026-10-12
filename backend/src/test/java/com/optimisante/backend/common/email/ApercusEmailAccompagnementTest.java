package com.optimisante.backend.common.email;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Écrit les deux messages d'accompagnement dans des fichiers HTML, à ouvrir au navigateur.
 *
 * <p><b>Pourquoi un test et non un script.</b> Un aperçu produit par un script à part finit
 * par montrer autre chose que ce qui part réellement : le script vieillit, le message change,
 * et personne ne s'en aperçoit. Ici, l'aperçu est le message — il sort du même code, par le
 * même chemin, et le test échoue si le gabarit cesse de porter ce qu'il doit porter.</p>
 *
 * <p>Les données sont fictives, et volontairement reconnaissables comme telles.</p>
 */
class ApercusEmailAccompagnementTest {

    /** À la racine du module : un chemin qu'on retrouve sans le chercher. */
    private static final Path DOSSIER = Path.of("target", "apercus-email");

    @Test
    @DisplayName("les deux messages sont écrits en HTML, prêts à être ouverts")
    void ecrireLesApercus() throws Exception {
        var expediteur = mock(org.springframework.mail.javamail.JavaMailSender.class);
        var journal = mock(EmailLogWriter.class);
        var session = jakarta.mail.Session.getInstance(new java.util.Properties());
        when(expediteur.createMimeMessage())
                .thenAnswer(i -> new jakarta.mail.internet.MimeMessage(session));

        EmailService service = new EmailService(expediteur, journal);
        injecter(service, "fromAddress", "ne-pas-repondre@optimisante.com");
        injecter(service, "frontendBaseUrl", "https://optimisante.com");

        service.sendAccompagnementRecu(
                "samba.nkounkou@exemple.cg", "Dr Samba Nkounkou", "Préparation EVC",
                "sous 48 h ouvrées", "infos@optimisante.com");

        service.sendAccompagnementAlerteAdmin(
                "mobilite@optimisante.com", "Dr Samba Nkounkou", "Médecin", "Congo",
                "Préparation EVC",
                "https://optimisante.com/admin/demandes-accompagnement/" + UUID.randomUUID());

        ArgumentCaptor<jakarta.mail.internet.MimeMessage> capture =
                ArgumentCaptor.forClass(jakarta.mail.internet.MimeMessage.class);
        verify(expediteur, times(2)).send(capture.capture());

        Files.createDirectories(DOSSIER);
        String[] noms = {"accuse-de-reception.html", "alerte-administration.html"};
        for (int i = 0; i < 2; i++) {
            var message = capture.getAllValues().get(i);
            // saveChanges() AVANT de lire : sans lui, getContent() rend la chaîne décodée
            // avec l'encodage de la plateforme, et les accents du gabarit se perdent. Le
            // défaut était dans cet aperçu, pas dans les messages envoyés — l'envoi réel
            // passe par send(), qui finalise le message.
            message.saveChanges();
            String html = new String(message.getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8);
            Files.writeString(DOSSIER.resolve(noms[i]), html, StandardCharsets.UTF_8);
            assertThat(html).isNotBlank();
        }

        // Ce que l'accusé doit porter, et qu'une refonte du gabarit ne doit pas emporter.
        String accuse = Files.readString(DOSSIER.resolve(noms[0]), StandardCharsets.UTF_8);
        assertThat(accuse)
                .as("le délai annoncé vient du paramètre, pas du code")
                .contains("sous 48 h");
        assertThat(accuse)
                .as("l'adresse de réponse est surveillée, et visible")
                .contains("infos@optimisante.com");
        assertThat(accuse)
                .as("la mention « pas un organisme officiel » est le premier message reçu")
                .contains("pas un organisme officiel");

        // Ce que l'alerte ne doit PAS porter : le message du praticien parle de sa situation,
        // et une boîte aux lettres ne s'anonymise pas.
        String alerte = Files.readString(DOSSIER.resolve(noms[1]), StandardCharsets.UTF_8);
        assertThat(alerte).contains("Dr Samba Nkounkou");
        assertThat(alerte).contains("/admin/demandes-accompagnement/");

        System.out.println("Aperçus écrits dans " + DOSSIER.toAbsolutePath());
    }

    /** Les deux adresses sont injectées par {@code @Value} : en test, on les pose à la main. */
    private static void injecter(Object cible, String champ, String valeur) throws Exception {
        var f = EmailService.class.getDeclaredField(champ);
        f.setAccessible(true);
        f.set(cible, valeur);
    }
}

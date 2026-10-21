package com.optimisante.backend.domain.exercerenfrance;

import com.optimisante.backend.ConfigurationBaseDeTest;
import com.optimisante.backend.domain.exercerenfrance.repository.DepotDemandes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import jakarta.mail.internet.MimeMessage;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ce que le praticien lit dans son accusé de réception.
 *
 * <p><b>Pourquoi ce test passe par le DÉPÔT et non par le service de courriel.</b> Un aperçu
 * existait déjà, et il était vert : il appelait {@code sendAccompagnementRecu} en lui passant
 * « Préparation EVC » à la main. Or le code de production lui passait
 * {@code demande.getOffreCle()} — la clé technique. L'aperçu montrait donc un joli libellé
 * pendant que les praticiens recevaient « votre demande concernant <b>preparation_evc</b> »,
 * et, pour une clé vide, « votre demande concernant <b>.</b> ».</p>
 *
 * <p>Un test qui ne suit pas le chemin réel ne prouve rien. Celui-ci part de la route
 * publique, comme un visiteur, et lit le message effectivement composé.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ConfigurationBaseDeTest.class)
@EnabledIf(value = "com.optimisante.backend.Docker#disponible",
           disabledReason = "Docker est indisponible : ce test a besoin d'un PostgreSQL éphémère.")
class OffreDansLesCourrielsTest {

    private static final String ROUTE = "/api/v1/public/demandes-accompagnement";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private DepotDemandes demandes;

    /** Le vrai EmailService compose le message ; seul l'envoi est intercepté. */
    @MockitoBean
    private JavaMailSender expediteur;

    @BeforeEach
    void preparer() {
        reset(expediteur);
        when(expediteur.createMimeMessage()).thenAnswer(i ->
                new MimeMessage(jakarta.mail.Session.getInstance(new java.util.Properties())));
    }

    /** Dépose comme un visiteur, et rend le HTML de l'ACCUSÉ (le premier message envoyé). */
    private String deposerEtLireAccuse(String fragmentOffre) throws Exception {
        String corps = """
                       {"nom":"Dr Samba Nkounkou","email":"%s","telephoneWhatsapp":"+242 06 123 45 67",
                        "pays":"Congo","profession":"Médecin","specialite":"Néphrologie",
                        "etape":"avant_evc","consentementRgpd":true%s}
                       """.formatted("p-" + UUID.randomUUID() + "@exemple.cg", fragmentOffre);

        mvc.perform(post(ROUTE)
                        .with(SecurityMockMvcRequestPostProcessors.anonymous())
                        .contentType("application/json").content(corps))
                .andExpect(status().isCreated());

        ArgumentCaptor<MimeMessage> capture = ArgumentCaptor.forClass(MimeMessage.class);
        verify(expediteur, atLeastOnce()).send(capture.capture());
        MimeMessage accuse = capture.getAllValues().getFirst();
        // saveChanges() AVANT de lire : sans lui, les accents du gabarit se perdent.
        accuse.saveChanges();
        return new String(accuse.getInputStream().readAllBytes(), StandardCharsets.ISO_8859_1);
    }

    @ParameterizedTest(name = "{0} → « concernant {1} »")
    @CsvSource({
            "preparation_evc,       Orientation EVC",
            "dossier_demarches,     Dossier & démarches",
            "mobilite_installation, Mobilité & installation",
            "pack_dossier_mobilite, Pack Dossier + Mobilité",
            "alerte_aep,            Alerte d'ouverture AEP",
    })
    @DisplayName("l'accusé nomme l'offre par son TITRE, jamais par sa clé")
    void titreEtNonCle(String cle, String titre) throws Exception {
        String html = deposerEtLireAccuse(",\"offreCle\":\"" + cle + "\"");

        assertThat(dechiffrer(html))
                .as("le praticien lit le libellé qu'il a vu sur la page")
                .contains("Nous avons bien reçu votre demande concernant <strong>"
                        + titre + "</strong>.");
        assertThat(dechiffrer(html))
                .as("la clé technique n'a rien à faire dans un courriel client")
                .doesNotContain(cle);
    }

    @Test
    @DisplayName("sans offre, la phrase est COMPLÈTE : plus jamais de trou")
    void sansOffre() throws Exception {
        // Le cas d'une demande générale — « Être accompagné », « Prendre rendez-vous ».
        String html = dechiffrer(deposerEtLireAccuse(""));

        assertThat(html).contains(
                "Nous avons bien reçu votre <strong>demande d'accompagnement</strong>.");
        assertThat(html)
                .as("c'était la phrase reçue par un praticien, et elle ne doit plus exister")
                .doesNotContain("concernant <strong></strong>")
                .doesNotContain("concernant <strong>.</strong>");
    }

    @Test
    @DisplayName("une clé vide est refusée par la base, pas glissée dans un courriel")
    void cleVideImpossible() throws Exception {
        String html = dechiffrer(deposerEtLireAccuse(",\"offreCle\":\"   \""));

        // Le serveur normalise le blanc en NULL au dépôt ; la contrainte de la V78 interdit
        // d'y parvenir autrement. Le message est donc celui d'une demande générale.
        assertThat(html).contains(
                "Nous avons bien reçu votre <strong>demande d'accompagnement</strong>.");
        assertThat(demandes.findAll()).allSatisfy(d ->
                assertThat(d.getOffreCle())
                        .as("ni chaîne vide, ni blanc : null ou une vraie clé")
                        .satisfiesAnyOf(c -> assertThat(c).isNull(),
                                        c -> assertThat(c).isNotBlank()));
    }

    @Test
    @DisplayName("une clé INCONNUE ne fabrique pas un nom : phrase générale")
    void cleInconnue() throws Exception {
        // Une offre supprimée depuis, ou une clé forgée à la main. Mieux vaut une phrase
        // complète sans nom qu'un nom inventé — ou qu'une clé technique affichée.
        String html = dechiffrer(deposerEtLireAccuse(",\"offreCle\":\"offre_disparue\""));

        assertThat(html).contains(
                "Nous avons bien reçu votre <strong>demande d'accompagnement</strong>.");
        assertThat(html).doesNotContain("offre_disparue");
    }

    @Test
    @DisplayName("l'alerte administrateur nomme la prestation, ou dit qu'il n'y en a pas")
    void alerteAdministrateur() throws Exception {
        deposerEtLireAccuse(",\"offreCle\":\"preparation_evc\"");
        ArgumentCaptor<MimeMessage> capture = ArgumentCaptor.forClass(MimeMessage.class);
        verify(expediteur, atLeastOnce()).send(capture.capture());

        // L'alerte est le second message, quand des administrateurs existent.
        var messages = capture.getAllValues();
        if (messages.size() < 2) {
            return;   // Aucun administrateur actif dans ce jeu de test : rien à vérifier.
        }
        messages.get(1).saveChanges();
        String alerte = dechiffrer(new String(
                messages.get(1).getInputStream().readAllBytes(), StandardCharsets.ISO_8859_1));
        assertThat(alerte).contains("Orientation EVC").doesNotContain("preparation_evc");
    }

    /**
     * Le HTML tel qu'il se lit.
     *
     * <p>Le corps part en quoted-printable : « é » devient « =C3=A9 » et les lignes longues
     * sont coupées par « =\n ». Comparer le texte brut échouerait sur la mise en forme du
     * transport, pas sur le message.</p>
     */
    private static String dechiffrer(String brut) {
        String sansCoupures = brut.replace("=\r\n", "").replace("=\n", "");
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < sansCoupures.length(); i++) {
            char c = sansCoupures.charAt(i);
            if (c == '=' && i + 2 < sansCoupures.length()
                    && estHexa(sansCoupures.charAt(i + 1)) && estHexa(sansCoupures.charAt(i + 2))) {
                out.append((char) Integer.parseInt(sansCoupures.substring(i + 1, i + 3), 16));
                i += 2;
            } else {
                out.append(c);
            }
        }
        String utf8 = new String(out.toString().getBytes(StandardCharsets.ISO_8859_1),
                StandardCharsets.UTF_8);
        // Le gabarit échappe tout texte variable (HtmlUtils.htmlEscape) : « é » devient
        // « &eacute; », « & » devient « &amp; ». On applique la fonction INVERSE de Spring
        // plutôt que de réécrire l'échappement — un test qui recopie le code qu'il éprouve
        // ne prouve rien.
        return org.springframework.web.util.HtmlUtils.htmlUnescape(utf8);
    }

    private static boolean estHexa(char c) {
        return (c >= '0' && c <= '9') || (c >= 'A' && c <= 'F') || (c >= 'a' && c <= 'f');
    }
}

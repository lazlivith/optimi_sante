package com.optimisante.backend.domain.exercerenfrance;

import com.optimisante.backend.ConfigurationBaseDeTest;
import com.optimisante.backend.domain.exercerenfrance.repository.DepotDemandes;
import com.optimisante.backend.domain.exercerenfrance.repository.DepotOffres;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Deux comportements que la recette a fait changer de sens.
 *
 * <p><b>La restauration.</b> Elle remettait l'ancienne version EN LIGNE, d'un clic et sans
 * relecture. La recette l'a établi en restaurant la version 1 : la page publique a aussitôt
 * réaffiché des promesses commerciales retirées la veille pour raison juridique. Elle recopie
 * désormais dans le BROUILLON, et ne publie rien.</p>
 *
 * <p><b>Le piège à robots.</b> Il répondait 202 là où un dépôt accepté rend 201 — un écart
 * qu'un robot lit, alors que le commentaire du code promettait l'inverse. Le dépôt est
 * désormais conservé avec le statut « suspect », sans e-mail, et répond comme un dépôt
 * accepté.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ConfigurationBaseDeTest.class)
@EnabledIf(value = "com.optimisante.backend.Docker#disponible",
           disabledReason = "Docker est indisponible : ce test a besoin d'un PostgreSQL éphémère.")
class RestaurationEtPiegeTest {

    private static final String ADMIN = "/api/v1/admin/exercer-en-france";
    private static final String PUBLIQUE = "/api/v1/public/demandes-accompagnement";
    private static final String SUPER = "11111111-1111-1111-1111-111111111111";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private DepotDemandes demandes;

    @Autowired
    private DepotOffres offres;

    private static String demande(String extra) {
        return """
               {"nom":"Dr Nkounkou","email":"%s","telephoneWhatsapp":"+242 06 123 45 67",
                "pays":"Congo","profession":"Médecin","specialite":"Néphrologie",
                "etape":"avant_evc","offreCle":"preparation_evc","consentementRgpd":true%s}
               """.formatted("p-" + UUID.randomUUID() + "@exemple.cg", extra);
    }

    // ── Le piège ────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("un dépôt piégé répond EXACTEMENT comme un dépôt accepté")
    void piegeIndiscernable() throws Exception {
        var normal = mvc.perform(post(PUBLIQUE)
                        .with(SecurityMockMvcRequestPostProcessors.anonymous())
                        .contentType("application/json").content(demande("")))
                .andReturn().getResponse();

        var piege = mvc.perform(post(PUBLIQUE)
                        .with(SecurityMockMvcRequestPostProcessors.anonymous())
                        .contentType("application/json")
                        .content(demande(",\"piege\":\"rempli\"")))
                .andReturn().getResponse();

        // Le code HTTP est ce qu'un robot lit en premier. Un 202 contre un 201 lui apprenait
        // qu'il avait été repéré — exactement ce que le piège est censé ne pas faire.
        assertThat(piege.getStatus()).isEqualTo(normal.getStatus()).isEqualTo(201);

        String texteNormal = normal.getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        String textePiege = piege.getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        // Le message aussi : « Votre demande n'a pas pu être enregistrée » était un aveu, et
        // il alarmait en prime le praticien qu'un filtre avait écarté par erreur.
        assertThat(messageDe(textePiege)).isEqualTo(messageDe(texteNormal));
    }

    @Test
    @DisplayName("un dépôt piégé est CONSERVÉ, avec le statut « suspect »")
    void piegeConserve() throws Exception {
        String corps = mvc.perform(post(PUBLIQUE)
                        .with(SecurityMockMvcRequestPostProcessors.anonymous())
                        .contentType("application/json")
                        .content(demande(",\"piege\":\"rempli\"")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID id = UUID.fromString(identifiantDe(corps));
        var d = demandes.findById(id).orElseThrow();
        // L'effacer d'office perdrait en silence le praticien qu'un filtre a mal jugé.
        assertThat(d.getStatut()).isEqualTo("suspect");
    }

    @Test
    @DisplayName("un envoi trop rapide est marqué suspect, et non refusé")
    void envoiTropRapide() throws Exception {
        String corps = mvc.perform(post(PUBLIQUE)
                        .with(SecurityMockMvcRequestPostProcessors.anonymous())
                        .contentType("application/json")
                        .content(demande(",\"ouvertDepuisMs\":10")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        assertThat(demandes.findById(UUID.fromString(identifiantDe(corps))).orElseThrow()
                .getStatut()).isEqualTo("suspect");
    }

    @Test
    @DisplayName("le pays est obligatoire : le régime de TVA en dépend")
    void paysObligatoire() throws Exception {
        String sansPays = """
                          {"nom":"Dr Nkounkou","email":"sans-pays@exemple.cg",
                           "offreCle":"preparation_evc","consentementRgpd":true}
                          """;
        mvc.perform(post(PUBLIQUE)
                        .with(SecurityMockMvcRequestPostProcessors.anonymous())
                        .contentType("application/json").content(sansPays))
                .andExpect(status().isBadRequest());
    }

    // ── La restauration ─────────────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = SUPER, roles = "SUPER_ADMIN")
    @DisplayName("restaurer recopie dans le BROUILLON et ne publie rien")
    void restaurationSansPublication() throws Exception {
        // Version 1 : l'état du seed.
        mvc.perform(post(ADMIN + "/publier")).andExpect(status().isOk());

        // On modifie une offre, puis on publie : la version 2 porte le nouveau prix.
        var offre = offres.findAll().stream()
                .filter(o -> "preparation_evc".equals(o.getCle())).findFirst().orElseThrow();
        mvc.perform(put(ADMIN + "/offres/" + offre.getId())
                        .contentType("application/json")
                        .content("""
                                 {"cle":"preparation_evc","titre":"Orientation EVC",
                                  "libelleBouton":"Être orienté","devise":"EUR","prix":777}
                                 """))
                .andExpect(status().isOk());
        mvc.perform(post(ADMIN + "/publier")).andExpect(status().isOk());

        int versionsAvant = nombreDeVersions();

        String enLigneAvant = mvc.perform(get("/api/v1/public/exercer-en-france"))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(enLigneAvant).contains("777");

        // Restauration de la version 1.
        mvc.perform(post(ADMIN + "/versions/1/restaurer"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versionSource").value(1));
        // La version 1 est celle que le seed publie au démarrage : elle porte le prix d'origine.

        // LE POINT CENTRAL : la page publique n'a pas bougé. Rien n'a été publié.
        String enLigneApres = mvc.perform(get("/api/v1/public/exercer-en-france"))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(enLigneApres).contains("777");

        // Le brouillon, lui, porte le prix de la version 1.
        var apres = offres.findById(offre.getId()).orElseThrow();
        assertThat(apres.getPrix()).isNotNull().isNotEqualByComparingTo("777");

        // Et aucune version n'a été créée : restaurer n'est plus un acte de publication.
        assertThat(nombreDeVersions()).isEqualTo(versionsAvant);
    }

    @Test
    @WithMockUser(username = SUPER, roles = "ADMIN_MOBILITE")
    @DisplayName("la Mobilité ne restaure toujours pas")
    void restaurationRefuseeALaMobilite() throws Exception {
        mvc.perform(post(ADMIN + "/versions/1/restaurer")).andExpect(status().isForbidden());
    }

    private int nombreDeVersions() throws Exception {
        String json = mvc.perform(get(ADMIN + "/versions"))
                .andReturn().getResponse()
                .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        return json.split("\"version\"").length - 1;
    }

    private static String messageDe(String json) {
        int i = json.indexOf("\"message\"");
        return i < 0 ? "" : json.substring(i);
    }

    private static String identifiantDe(String json) {
        int i = json.indexOf("\"id\":\"") + 6;
        return json.substring(i, json.indexOf('"', i));
    }
}

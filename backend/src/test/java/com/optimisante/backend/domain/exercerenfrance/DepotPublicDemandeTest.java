package com.optimisante.backend.domain.exercerenfrance;

import com.optimisante.backend.ConfigurationBaseDeTest;
import com.optimisante.backend.domain.exercerenfrance.repository.DepotDemandes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Le dépôt d'une demande par un visiteur non connecté.
 *
 * <p><b>Le point central est le tenant.</b> La route est ouverte : elle n'a ni session, ni
 * jeton, donc aucune source de tenant digne de foi. Les deux seules que l'appelant peut
 * fournir — l'en-tête {@code X-Tenant-Id} et un champ du corps — doivent rester sans effet.
 * Le formulaire de candidature des médecins lit aujourd'hui le tenant dans le corps
 * ({@code dto.getTenantCode()}) ; c'est une dette identifiée, et ce qui suit vérifie qu'elle
 * n'a pas été recopiée ici.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ConfigurationBaseDeTest.class)
@EnabledIf(value = "com.optimisante.backend.Docker#disponible",
           disabledReason = "Docker est indisponible : ce test a besoin d'un PostgreSQL éphémère.")
@WithAnonymousUser
class DepotPublicDemandeTest {

    private static final String ROUTE = "/api/v1/public/demandes-accompagnement";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DepotDemandes demandes;

    /** Une adresse neuve à chaque appel : le plafond quotidien se compte par adresse. */
    private static String adresse() {
        return "praticien-" + UUID.randomUUID() + "@exemple.cg";
    }

    private static String corps(String email, String extra) {
        return """
               {"nom":"Dr Nkounkou","email":"%s","telephoneWhatsapp":"+242 06 123 45 67",
                "pays":"Congo","profession":"Médecin","specialite":"Néphrologie",
                "etape":"avant_evc","offreCle":"preparation_evc",
                "message":"Je prépare les EVC.","consentementRgpd":true%s}
               """.formatted(email, extra);
    }

    @Test
    @DisplayName("la demande est créée sur FR_MAIN malgré un en-tête et un corps hostiles")
    void leTenantVientDuServeur() throws Exception {
        // Un tenant concurrent existe en base : s'il était retenu, la demande y atterrirait.
        UUID intrus = UUID.randomUUID();
        jdbc.update("INSERT INTO tenants (id, code, name, domain) VALUES (?, ?, ?, ?)",
                intrus, "INTRUS", "Tenant intrus", "intrus.invalid");

        String email = adresse();

        // D'abord l'en-tête seul : un corps propre, mais un X-Tenant-Id qui désigne l'intrus.
        mvc.perform(post(ROUTE)
                        .header("X-Tenant-Id", intrus.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corps(email, "")))
                .andExpect(status().isCreated());

        String codeRetenu = jdbc.queryForObject(
                "SELECT t.code FROM accompaniment_requests d JOIN tenants t ON t.id = d.tenant_id "
                + "WHERE d.email = ?", String.class, email);
        assertThat(codeRetenu)
                .as("le tenant vient de la configuration, pas de l'en-tête")
                .isEqualTo("FR_MAIN");

        // Puis un champ de tenant dans le CORPS, en plus de l'en-tête. Le DTO n'en déclare
        // aucun : le champ est ignoré, et la demande part tout de même chez FR_MAIN.
        //
        // C'est bien l'IGNORANCE qui est éprouvée ici, et non un refus. Un refus dépendrait
        // de la configuration de désérialisation, qui peut changer sans que personne y pense ;
        // l'absence du champ dans le DTO, elle, ne dépend de rien.
        String secondEmail = adresse();
        mvc.perform(post(ROUTE)
                        .header("X-Tenant-Id", intrus.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corps(secondEmail, ",\"tenantId\":\"" + intrus + "\",\"tenantCode\":\"INTRUS\"")))
                .andExpect(status().isCreated());

        assertThat(jdbc.queryForObject(
                "SELECT t.code FROM accompaniment_requests d JOIN tenants t ON t.id = d.tenant_id "
                + "WHERE d.email = ?", String.class, secondEmail))
                .as("un champ tenant dans le corps reste sans effet")
                .isEqualTo("FR_MAIN");

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM accompaniment_requests WHERE tenant_id = ?", Long.class, intrus))
                .as("aucune demande n'a atterri chez l'intrus")
                .isZero();
    }

    @Test
    @DisplayName("le consentement absent est refusé, et rien n'est écrit")
    void consentementObligatoire() throws Exception {
        long avant = demandes.count();
        mvc.perform(post(ROUTE).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                 {"nom":"Dr Test","email":"%s","consentementRgpd":false}
                                 """.formatted(adresse())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("consentement")));
        assertThat(demandes.count()).isEqualTo(avant);
    }

    @Test
    @DisplayName("une adresse mal formée est refusée")
    void emailValide() throws Exception {
        mvc.perform(post(ROUTE).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                 {"nom":"Dr Test","email":"pas-une-adresse","consentementRgpd":true}
                                 """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("mail")));
    }

    @Test
    @DisplayName("le nom est obligatoire")
    void nomObligatoire() throws Exception {
        mvc.perform(post(ROUTE).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                 {"email":"%s","consentementRgpd":true}
                                 """.formatted(adresse())))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("le champ piège rempli écarte le dépôt, sans le dire")
    void champPiege() throws Exception {
        long avant = demandes.count();
        mvc.perform(post(ROUTE).contentType(MediaType.APPLICATION_JSON)
                        .content(corps(adresse(), ",\"piege\":\"http://spam.example\"")))
                // Accepté en apparence : annoncer « champ piège rempli » enseignerait
                // comment passer la fois suivante.
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").doesNotExist());
        assertThat(demandes.count()).isEqualTo(avant);
    }

    @Test
    @DisplayName("un formulaire envoyé en moins de trois secondes est écarté")
    void tropRapide() throws Exception {
        long avant = demandes.count();
        mvc.perform(post(ROUTE).contentType(MediaType.APPLICATION_JSON)
                        .content(corps(adresse(), ",\"ouvertDepuisMs\":800")))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").doesNotExist());
        assertThat(demandes.count()).isEqualTo(avant);
    }

    @Test
    @DisplayName("la quatrième demande du jour pour une même adresse est écartée")
    void plafondQuotidien() throws Exception {
        String email = adresse();
        for (int i = 1; i <= 3; i++) {
            mvc.perform(post(ROUTE).contentType(MediaType.APPLICATION_JSON)
                            .content(corps(email, ",\"ouvertDepuisMs\":9000")))
                    .andExpect(status().isCreated());
        }
        // Celui-là concerne un praticien réel qui s'impatiente : le message l'explique,
        // contrairement aux deux filtres précédents.
        mvc.perform(post(ROUTE).contentType(MediaType.APPLICATION_JSON)
                        .content(corps(email, ",\"ouvertDepuisMs\":9000")))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("plusieurs demandes")));

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM accompaniment_requests WHERE email = ?", Long.class, email))
                .isEqualTo(3L);
    }

    @Test
    @DisplayName("aucun refus ne renvoie de trace technique au visiteur")
    void aucuneFuiteTechnique() throws Exception {
        String reponse = mvc.perform(post(ROUTE).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nom\":\"\",\"email\":\"x\",\"consentementRgpd\":false}"))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();

        // Une pile d'appels ou un nom de classe renseigne un attaquant sur ce qui tourne,
        // et n'apprend rien au praticien.
        assertThat(reponse)
                .doesNotContain("com.optimisante")
                .doesNotContain("Exception")
                .doesNotContain("at org.springframework");
    }

    @Test
    @DisplayName("une demande acceptée ouvre son fil, sans auteur")
    void leFilSOuvre() throws Exception {
        String email = adresse();
        mvc.perform(post(ROUTE).contentType(MediaType.APPLICATION_JSON)
                        .content(corps(email, ",\"ouvertDepuisMs\":9000")))
                .andExpect(status().isCreated());

        Long evenements = jdbc.queryForObject("""
                SELECT count(*) FROM accompaniment_request_events e
                JOIN accompaniment_requests d ON d.id = e.request_id
                WHERE d.email = ? AND e.type = 'creation' AND e.auteur_id IS NULL
                """, Long.class, email);
        assertThat(evenements).isEqualTo(1L);
    }
}

package com.optimisante.backend.domain.exercerenfrance;

import com.optimisante.backend.ConfigurationBaseDeTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Les filtres de la liste, et la trace que laisse un export.
 *
 * <p><b>Pourquoi les filtres méritent un test.</b> Ils ont été écrits deux fois. La première
 * version, en JPQL avec la forme {@code (:param IS NULL OR …)}, compilait et démarrait, mais
 * PostgreSQL refusait la requête à l'exécution : il ne sait pas typer un paramètre qui n'est
 * comparé qu'à {@code NULL}. Une erreur qu'aucune relecture n'attrape et qu'aucun test
 * unitaire ne voit — seul un vrai PostgreSQL la montre.</p>
 *
 * <p><b>Pourquoi l'audit en mérite un aussi.</b> Un export sort des données personnelles vers
 * un fichier que plus rien ne protège. Le jour où l'une d'elles réapparaît ailleurs, la seule
 * question qui compte est de savoir qui l'a sortie et quand — et la réponse n'existe que si
 * la ligne d'audit a bien été écrite. Ce test vérifie aussi l'inverse : que cette ligne ne
 * contient PAS ce qui a été cherché, car on y tape des noms et des adresses.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ConfigurationBaseDeTest.class)
@EnabledIf(value = "com.optimisante.backend.Docker#disponible",
           disabledReason = "Docker est indisponible : ce test a besoin d'un PostgreSQL éphémère.")
@WithMockUser(roles = "ADMIN_MOBILITE")
class DemandesFiltresEtExportTest {

    private static final String ROUTE = "/api/v1/admin/demandes-accompagnement";
    private static final String PUBLIQUE = "/api/v1/public/demandes-accompagnement";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void table_rase() {
        jdbc.update("DELETE FROM audit_logs WHERE action = 'ACCOMPAGNEMENT_EXPORT'");
        jdbc.update("DELETE FROM accompaniment_request_events");
        jdbc.update("DELETE FROM accompaniment_requests");
    }

    /** Dépose une demande par la route publique, comme un vrai visiteur. */
    private void deposer(String nom, String email, String profession, String offre)
            throws Exception {
        mvc.perform(post(PUBLIQUE)
                        .contentType("application/json")
                        .with(org.springframework.security.test.web.servlet.request
                                .SecurityMockMvcRequestPostProcessors.anonymous())
                        .content("""
                                 {"nom":"%s","email":"%s","telephoneWhatsapp":"+242 06 123 45 67",
                                  "pays":"Congo","profession":"%s","specialite":"Néphrologie",
                                  "etape":"avant_evc","offreCle":"%s","consentementRgpd":true}
                                 """.formatted(nom, email, profession, offre)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("sans filtre, la liste rend tout ; avec un filtre, elle rend ce qu'il désigne")
    void filtresAppliques() throws Exception {
        deposer("Dr Nkounkou", "a@exemple.cg", "Médecin", "preparation_evc");
        deposer("Dr Traoré", "b@exemple.cg", "Sage-femme", "alerte_aep");

        // Aucun filtre : la requête doit S'EXÉCUTER — c'est précisément ce que la version
        // JPQL ne faisait pas, faute de typage des paramètres nuls.
        mvc.perform(get(ROUTE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));

        mvc.perform(get(ROUTE).param("profession", "Sage-femme"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].nom").value("Dr Traoré"));

        mvc.perform(get(ROUTE).param("offreCle", "alerte_aep"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        mvc.perform(get(ROUTE).param("statut", "nouveau"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));

        // La recherche porte sur le nom, l'adresse et la spécialité.
        mvc.perform(get(ROUTE).param("recherche", "traoré"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        // Plusieurs filtres se combinent, et non s'annulent.
        mvc.perform(get(ROUTE).param("profession", "Médecin").param("offreCle", "alerte_aep"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("« jusqu'au » inclut la journée entière, et non son premier instant")
    void derniereJourneeIncluse() throws Exception {
        deposer("Dr Nkounkou", "a@exemple.cg", "Médecin", "preparation_evc");
        String aujourdHui = java.time.LocalDate.now(java.time.ZoneId.of("Europe/Paris")).toString();

        // Prendre « jusqu'au 12 » à minuit amputerait tout le 12 — et c'est toujours le jour
        // qu'on cherche quand on sort la liste du matin.
        mvc.perform(get(ROUTE).param("depuis", aujourdHui).param("jusqua", aujourdHui))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("la liste n'emporte ni le message du praticien ni les notes internes")
    void listeSansDonneesInutiles() throws Exception {
        deposer("Dr Nkounkou", "a@exemple.cg", "Médecin", "preparation_evc");

        String corps = mvc.perform(get(ROUTE))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // Ce qui part vers le navigateur est lisible par qui ouvre les outils du navigateur :
        // un écran de liste n'a besoin ni de l'un, ni de l'autre.
        assertThat(corps).doesNotContain("notesInternes").doesNotContain("\"message\"");
    }

    @Test
    @DisplayName("chaque export laisse une ligne d'audit, avec ses filtres et son volume")
    void exportTrace() throws Exception {
        deposer("Dr Nkounkou", "a@exemple.cg", "Médecin", "preparation_evc");

        mvc.perform(get(ROUTE + "/export.csv").param("profession", "Médecin"))
                .andExpect(status().isOk());

        List<String> traces = jdbc.queryForList(
                "SELECT summary FROM audit_logs WHERE action = 'ACCOMPAGNEMENT_EXPORT'",
                String.class);

        assertThat(traces).hasSize(1);
        assertThat(traces.getFirst())
                .contains("1 ligne(s) exportée(s)")
                .contains("profession=Médecin");
    }

    @Test
    @DisplayName("l'audit retient qu'on a cherché, jamais ce qu'on a cherché")
    void rechercheAbsenteDeLAudit() throws Exception {
        deposer("Dr Nkounkou", "a@exemple.cg", "Médecin", "preparation_evc");

        mvc.perform(get(ROUTE + "/export.csv").param("recherche", "Nkounkou"))
                .andExpect(status().isOk());

        String trace = jdbc.queryForObject(
                "SELECT summary FROM audit_logs WHERE action = 'ACCOMPAGNEMENT_EXPORT'",
                String.class);

        // On tape là des noms et des adresses. Les inscrire au journal reviendrait à recréer,
        // dans la table censée surveiller les sorties de données personnelles, une collection
        // de données personnelles.
        assertThat(trace).contains("recherche=(texte)").doesNotContain("Nkounkou");
    }

    @Test
    @DisplayName("l'export des alertes ne sort que les contacts, et se trace à part")
    void exportAlertesMinimal() throws Exception {
        deposer("Dr Nkounkou", "a@exemple.cg", "Médecin", "preparation_evc");
        deposer("Dr Traoré", "b@exemple.cg", "Sage-femme", "alerte_aep");

        String csv = mvc.perform(get(ROUTE + "/export-alertes.csv"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        // Ce fichier sert à écrire à des gens, pas à les étudier : seule l'alerte AEP y est,
        // et sans son statut commercial ni son message.
        assertThat(csv).contains("Dr Traoré").doesNotContain("Dr Nkounkou");
        assertThat(csv).startsWith("﻿").contains("\"WhatsApp\"").doesNotContain("Statut");

        // Le numéro sort NU : ce fichier existe pour qu'on le colle dans WhatsApp.
        assertThat(csv).contains("\"+242 06 123 45 67\"");

        String trace = jdbc.queryForObject(
                "SELECT entity_id FROM audit_logs WHERE action = 'ACCOMPAGNEMENT_EXPORT'",
                String.class);
        assertThat(trace).isEqualTo("alertes-aep");
    }
}

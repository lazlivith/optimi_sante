package com.optimisante.backend.domain.exercerenfrance;

import com.optimisante.backend.ConfigurationBaseDeTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
import java.util.Map;

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

    /** Le nombre de demandes que la liste retourne pour ces paramètres. */
    private int compte(Map<String, String> params) throws Exception {
        var requete = get(ROUTE);
        params.forEach(requete::param);
        String corps = mvc.perform(requete)
                // Le 200 est la moitié de l'assertion : la version JPQL rendait un 500 ici,
                // PostgreSQL refusant un paramètre qu'il ne sait pas typer.
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                .readTree(corps).get("totalElements").asInt();
    }

    @Test
    @DisplayName("sans aucun filtre, la liste s'exécute et rend tout — le cas qui avait échoué")
    void sansAucunFiltre() throws Exception {
        deposer("Dr Nkounkou", "a@exemple.cg", "Médecin", "preparation_evc");
        deposer("Dr Traoré", "b@exemple.cg", "Sage-femme", "alerte_aep");

        assertThat(compte(Map.of())).isEqualTo(2);
    }

    /**
     * Chaque filtre seul.
     *
     * <p><b>Pourquoi un par un, et pas seulement combinés.</b> Le défaut qu'on cherche ici est
     * un défaut de TYPAGE : PostgreSQL refuse un paramètre qu'il ne peut pas typer parce qu'il
     * n'est comparé qu'à {@code NULL}. Il ne se déclenche donc que sur le paramètre resté
     * vide — c'est-à-dire sur tous les autres que celui qu'on essaie. Un seul test combinant
     * tout les éprouverait tous renseignés, soit exactement le cas qui marche.</p>
     */
    @ParameterizedTest(name = "{0}={1} → {2} demande(s)")
    @CsvSource({
            "statut,      nouveau,       2",
            "offreCle,    alerte_aep,    1",
            "offreCle,    preparation_evc, 1",
            "profession,  Sage-femme,    1",
            "recherche,   traoré,        1",
            "recherche,   a@exemple.cg,  1",
            "recherche,   néphrolog,     2",
            "depuis,      2000-01-01,    2",
            "jusqua,      2099-12-31,    2",
    })
    @DisplayName("chaque filtre seul s'applique, et les autres restent sans effet")
    void chaqueFiltreSeul(String champ, String valeur, int attendu) throws Exception {
        deposer("Dr Nkounkou", "a@exemple.cg", "Médecin", "preparation_evc");
        deposer("Dr Traoré", "b@exemple.cg", "Sage-femme", "alerte_aep");

        assertThat(compte(Map.of(champ, valeur))).isEqualTo(attendu);
    }

    @Test
    @DisplayName("la recherche porte sur le nom, l'adresse et la spécialité — et rien d'autre")
    void perimetreDeLaRecherche() throws Exception {
        deposer("Dr Nkounkou", "a@exemple.cg", "Médecin", "preparation_evc");

        assertThat(compte(Map.of("recherche", "nkounkou"))).isEqualTo(1);     // le nom
        assertThat(compte(Map.of("recherche", "a@exemple"))).isEqualTo(1);    // l'adresse
        assertThat(compte(Map.of("recherche", "néphrolog"))).isEqualTo(1);    // la spécialité

        // Ce test fige un PÉRIMÈTRE, dans les deux sens : ce qui est couvert, et ce qui ne
        // l'est pas. La profession a son propre filtre ; la verser aussi dans la recherche
        // ferait qu'un mot tapé là rendrait tantôt des dossiers, tantôt des professions
        // entières, sans que l'écran l'explique.
        assertThat(compte(Map.of("recherche", "Médecin"))).isZero();
        assertThat(compte(Map.of("recherche", "preparation_evc"))).isZero();
    }

    @Test
    @DisplayName("un filtre qui ne désigne personne rend une liste vide, pas une erreur")
    void filtreSansResultat() throws Exception {
        deposer("Dr Nkounkou", "a@exemple.cg", "Médecin", "preparation_evc");

        assertThat(compte(Map.of("profession", "Vétérinaire"))).isZero();
        assertThat(compte(Map.of("statut", "perdu"))).isZero();
        assertThat(compte(Map.of("recherche", "introuvable"))).isZero();
    }

    @Test
    @DisplayName("tous les filtres ensemble se combinent, et ne s'annulent pas")
    void tousLesFiltresCombines() throws Exception {
        deposer("Dr Nkounkou", "a@exemple.cg", "Médecin", "preparation_evc");
        deposer("Dr Traoré", "b@exemple.cg", "Sage-femme", "alerte_aep");

        Map<String, String> tous = Map.of(
                "statut", "nouveau",
                "offreCle", "preparation_evc",
                "profession", "Médecin",
                "depuis", "2000-01-01",
                "jusqua", "2099-12-31",
                "recherche", "Nkounkou");

        // Les six à la fois désignent le seul dossier qui les satisfait tous.
        assertThat(compte(tous)).isEqualTo(1);

        // Et un seul critère discordant suffit à ne plus rien désigner : la combinaison est
        // bien un ET, et non un OU qui aurait l'air de marcher sur les cas simples.
        Map<String, String> discordant = new java.util.HashMap<>(tous);
        discordant.put("profession", "Sage-femme");
        assertThat(compte(discordant)).isZero();
    }

    @Test
    @DisplayName("le raccourci « Alertes AEP » ne rend que les demandes d'alerte")
    void raccourciAlertesAep() throws Exception {
        deposer("Dr Nkounkou", "a@exemple.cg", "Médecin", "preparation_evc");
        deposer("Dr Traoré", "b@exemple.cg", "Sage-femme", "alerte_aep");

        String corps = mvc.perform(get(ROUTE).param("offreCle", "alerte_aep"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(corps).contains("Dr Traoré").doesNotContain("Dr Nkounkou");
    }

    @Test
    @DisplayName("l'export complet s'exécute lui aussi sans aucun filtre")
    void exportSansFiltre() throws Exception {
        deposer("Dr Nkounkou", "a@exemple.cg", "Médecin", "preparation_evc");
        deposer("Dr Traoré", "b@exemple.cg", "Sage-femme", "alerte_aep");

        // L'export passe par la même requête que la liste : s'il existait un paramètre mal
        // typé, il tomberait ici aussi — et personne ne s'en apercevrait avant un vrai export.
        String csv = mvc.perform(get(ROUTE + "/export.csv"))
                .andExpect(status().isOk())
                .andReturn().getResponse()
                .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        assertThat(csv).startsWith("﻿").contains("Dr Nkounkou").contains("Dr Traoré");
        assertThat(csv.lines().count()).isEqualTo(3);   // l'en-tête et les deux demandes
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

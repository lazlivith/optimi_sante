package com.optimisante.backend.domain.partnership;

import com.optimisante.backend.ConfigurationBaseDeTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Le dépôt anonyme de partenariat, au travers de la <b>vraie chaîne de sécurité</b>.
 *
 * <p>Les tests unitaires montrent que chaque pièce refuse ce qu'elle doit refuser. Celui-ci
 * montre qu'elles sont <b>branchées</b> : un filtre correct que {@code SecurityConfig}
 * n'insérerait pas, ou insérerait sur le mauvais chemin, passerait tous ses tests unitaires et
 * ne protégerait rien.</p>
 *
 * <p>Ce qu'il ne montre pas : que la requête trop grosse est refusée <i>avant</i> la lecture de
 * son corps. {@code MockMvc} ne passe pas par Tomcat et livre le corps déjà en mémoire ; seul
 * un appel HTTP réel le prouverait.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ConfigurationBaseDeTest.class)
@EnabledIf(value = "com.optimisante.backend.Docker#disponible",
           disabledReason = "Docker est indisponible : ce test a besoin d'un PostgreSQL éphémère.")
@WithAnonymousUser
class DepotAnonymePartenariatChaineTest {

    private static final String DEPOT = "/api/v1/partnership/requests";
    private static final String RELECTURE = "/api/v1/partnership/dossier/verification";

    private static final byte[] PDF = "%PDF-1.7\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    private static final byte[] EXECUTABLE = {'M', 'Z', (byte) 0x90, 0, 3, 0};

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    /** Une adresse de visiteur propre à chaque test : les plafonds se comptent par adresse. */
    private static String visiteur() {
        return "198.51.100." + (1 + Math.abs(UUID.randomUUID().hashCode() % 250));
    }

    private static MockMultipartHttpServletRequestBuilder depot(MockMultipartFile fichier, String visiteur) {
        return (MockMultipartHttpServletRequestBuilder) multipart(DEPOT)
                .file(fichier)
                .param("institutionName", "CHU de test")
                .param("contactPersonName", "Dr Martin")
                .param("contactEmail", "direction@chu.example")
                .param("contactPhone", "+33 5 56 00 00 00")
                .param("address", "1 place Amélie Raba-Léon, 33000 Bordeaux")
                .header("CF-Connecting-IP", visiteur);
    }

    private long demandesEnBase() {
        Long nombre = jdbc.queryForObject("SELECT count(*) FROM partnership_requests", Long.class);
        return nombre == null ? 0 : nombre;
    }

    @Test
    @DisplayName("le formulaire obtient son jeton sans être connecté, et la réponse n'est pas mise en cache")
    void jetonDeFormulaire() throws Exception {
        mvc.perform(get("/api/v1/partnership/formulaire"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.jeton").isNotEmpty());
    }

    @Test
    @DisplayName("sans jeton de formulaire : 400 lisible, et rien n'est écrit en base")
    void depotSansJeton() throws Exception {
        long avant = demandesEnBase();

        mvc.perform(depot(new MockMultipartFile("conventionFile", "convention.pdf", "application/pdf", PDF),
                        visiteur()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Rechargez la page")));

        assertThat(demandesEnBase()).isEqualTo(avant);
    }

    @Test
    @DisplayName("un jeton tout juste émis est refusé : le formulaire a été envoyé trop vite")
    void depotImmediat() throws Exception {
        String reponse = mvc.perform(get("/api/v1/partnership/formulaire"))
                .andReturn().getResponse().getContentAsString();
        String jeton = reponse.replaceAll(".*\"jeton\":\"([^\"]+)\".*", "$1");
        long avant = demandesEnBase();

        mvc.perform(depot(new MockMultipartFile("conventionFile", "convention.pdf", "application/pdf", PDF),
                        visiteur()).param("jeton", jeton))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("trop vite")));

        assertThat(demandesEnBase()).isEqualTo(avant);
    }

    @Test
    @DisplayName("une requête de 12 Mo est refusée en 413 par le filtre")
    void requeteTropGrosse() throws Exception {
        // MockMvc n'annonce aucune taille pour une requête multipart : on lui donne un corps de
        // 12 Mo, qui est ce que le filtre lit — la taille annoncée, pas celle du fichier.
        mvc.perform(depot(new MockMultipartFile("conventionFile", "convention.pdf", "application/pdf", PDF),
                        visiteur())
                        .with(requete -> {
                            requete.setContent(new byte[12 * 1024 * 1024]);
                            return requete;
                        }))
                .andExpect(status().is(413))
                .andExpect(jsonPath("$.message").value(containsString("10 Mo")));
    }

    @Test
    @DisplayName("relecture : un exécutable renommé en .xlsx revient comme une anomalie, sans être ouvert")
    void relectureDUnFaux() throws Exception {
        mvc.perform(multipart(RELECTURE)
                        .file(new MockMultipartFile("fichier", "dossier.xlsx", null, EXECUTABLE))
                        .header("CF-Connecting-IP", visiteur()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conforme").value(false))
                .andExpect(jsonPath("$.anomalies[0].probleme").value(containsString(".xlsx")));
    }

    @Test
    @DisplayName("onzième envoi d'une même adresse dans la minute : 429 avec Retry-After")
    void plafondParAdresse() throws Exception {
        String visiteur = "203.0.113.200";
        MockMultipartFile faux = new MockMultipartFile("fichier", "dossier.xlsx", null, EXECUTABLE);
        for (int i = 0; i < 10; i++) {
            mvc.perform(multipart(RELECTURE).file(faux).header("CF-Connecting-IP", visiteur))
                    .andExpect(status().isOk());
        }

        mvc.perform(multipart(RELECTURE).file(faux).header("CF-Connecting-IP", visiteur))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.message").value(containsString("Trop d'envois")));

        // Les modèles, eux, restent téléchargeables : seul le dépôt est plafonné.
        mvc.perform(get("/api/v1/partnership/dossier-modele").header("CF-Connecting-IP", visiteur))
                .andExpect(status().isOk());
    }
}

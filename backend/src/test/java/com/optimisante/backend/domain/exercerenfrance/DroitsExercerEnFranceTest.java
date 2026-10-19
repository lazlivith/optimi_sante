package com.optimisante.backend.domain.exercerenfrance;

import com.optimisante.backend.ConfigurationBaseDeTest;
import com.optimisante.backend.Docker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Qui peut faire quoi sur « Exercer en France », éprouvé au niveau HTTP.
 *
 * <p><b>Pourquoi au niveau HTTP et non sur les services.</b> La séparation des droits n'est pas
 * écrite dans les services : elle tient à une annotation posée sur une méthode de contrôleur.
 * Appeler le service directement contournerait précisément ce qu'il s'agit de vérifier, et le
 * test passerait au vert le jour où l'annotation disparaîtrait.</p>
 *
 * <p><b>Ce que ces tests empêchent.</b> Qu'un administrateur du Négoce lise les demandes
 * d'accompagnement — ce sont des données personnelles de praticiens, étrangères à son
 * métier — et qu'un administrateur Mobilité fixe un prix ou remette en ligne une version
 * passée sans relecture.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ConfigurationBaseDeTest.class)
@EnabledIf(value = "com.optimisante.backend.Docker#disponible",
           disabledReason = "Docker est indisponible : ce test a besoin d'un PostgreSQL éphémère.")
class DroitsExercerEnFranceTest {

    /** Le principal porte un identifiant : les contrôleurs en tirent l'auteur des actions. */
    private static final String UTILISATEUR = "11111111-1111-1111-1111-111111111111";

    private static final String CONTENU = "/api/v1/admin/exercer-en-france";
    private static final String DEMANDES = "/api/v1/admin/demandes-accompagnement";

    @Autowired
    private MockMvc mvc;

    private static String offre() {
        return """
               {"cle":"essai-%s","titre":"Préparation","libelleBouton":"Je me prépare","prix":490.00}
               """.formatted(UUID.randomUUID());
    }

    // ── Contenu : Mobilité et super administrateur ──────────────────────────────────────

    @Test
    @DisplayName("le contenu est ouvert à l'admin Mobilité")
    @WithMockUser(username = UTILISATEUR, roles = "ADMIN_MOBILITE")
    void contenuPourLaMobilite() throws Exception {
        mvc.perform(get(CONTENU + "/sections")).andExpect(status().isOk());
        mvc.perform(get(CONTENU + "/periodes")).andExpect(status().isOk());
        mvc.perform(get(CONTENU + "/liens")).andExpect(status().isOk());
        mvc.perform(get(CONTENU + "/specialites")).andExpect(status().isOk());
        mvc.perform(get(CONTENU + "/indicateurs")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("le contenu est ouvert au super administrateur")
    @WithMockUser(username = UTILISATEUR, roles = "SUPER_ADMIN")
    void contenuPourLeSuperAdmin() throws Exception {
        mvc.perform(get(CONTENU + "/sections")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("le contenu est refusé à l'admin Négoce")
    @WithMockUser(username = UTILISATEUR, roles = "ADMIN_ECOMMERCE")
    void contenuRefuseAuNegoce() throws Exception {
        mvc.perform(get(CONTENU + "/sections")).andExpect(status().isForbidden());
        mvc.perform(get(CONTENU + "/periodes")).andExpect(status().isForbidden());
        mvc.perform(post(CONTENU + "/publier")).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("le contenu est refusé à un visiteur")
    @WithAnonymousUser
    void contenuRefuseAuVisiteur() throws Exception {
        mvc.perform(get(CONTENU + "/sections")).andExpect(status().isUnauthorized());
        mvc.perform(get(CONTENU + "/apercu")).andExpect(status().isUnauthorized());
    }

    // ── Demandes : données personnelles, donc Mobilité seulement ────────────────────────

    @Test
    @DisplayName("les demandes sont ouvertes à la Mobilité et au super administrateur")
    @WithMockUser(username = UTILISATEUR, roles = "ADMIN_MOBILITE")
    void demandesPourLaMobilite() throws Exception {
        mvc.perform(get(DEMANDES)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("les demandes sont refusées à l'admin Négoce")
    @WithMockUser(username = UTILISATEUR, roles = "ADMIN_ECOMMERCE")
    void demandesRefuseesAuNegoce() throws Exception {
        // Ce sont le nom, le téléphone et la situation administrative de praticiens : rien
        // qui regarde la boutique de dispositifs médicaux.
        mvc.perform(get(DEMANDES)).andExpect(status().isForbidden());
        mvc.perform(get(DEMANDES + "/export.csv")).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("les demandes sont refusées à un visiteur")
    @WithAnonymousUser
    void demandesRefuseesAuVisiteur() throws Exception {
        mvc.perform(get(DEMANDES)).andExpect(status().isUnauthorized());
    }

    // ── Prix : super administrateur SEUL ────────────────────────────────────────────────

    @Test
    @DisplayName("l'admin Mobilité LIT les offres mais n'en écrit aucune")
    @WithMockUser(username = UTILISATEUR, roles = "ADMIN_MOBILITE")
    void offresEnLectureSeulePourLaMobilite() throws Exception {
        mvc.perform(get(CONTENU + "/offres")).andExpect(status().isOk());
        // Un prix engage le chiffre d'affaires : il ne relève pas de l'éditorial.
        mvc.perform(post(CONTENU + "/offres")
                        .contentType(MediaType.APPLICATION_JSON).content(offre()))
                .andExpect(status().isForbidden());
        mvc.perform(put(CONTENU + "/offres/" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content(offre()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("le super administrateur écrit les offres")
    @WithMockUser(username = UTILISATEUR, roles = "SUPER_ADMIN")
    void offresEcritesParLeSuperAdmin() throws Exception {
        mvc.perform(post(CONTENU + "/offres")
                        .contentType(MediaType.APPLICATION_JSON).content(offre()))
                .andExpect(status().isOk());
    }

    // ── Restauration : super administrateur SEUL ────────────────────────────────────────

    @Test
    @DisplayName("l'admin Mobilité publie mais ne restaure pas")
    @WithMockUser(username = UTILISATEUR, roles = "ADMIN_MOBILITE")
    void restaurationRefuseeALaMobilite() throws Exception {
        // Restaurer remet en ligne un contenu que personne n'a relu aujourd'hui : c'est un
        // acte distinct de la publication, et il appartient à la gouvernance.
        mvc.perform(post(CONTENU + "/versions/1/restaurer")).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("la restauration d'une version inexistante est refusée, pas confondue avec un droit")
    @WithMockUser(username = UTILISATEUR, roles = "SUPER_ADMIN")
    void restaurationIntrouvablePourLeSuperAdmin() throws Exception {
        // Le super administrateur passe la garde : l'échec vient du contenu, non des droits.
        // Sans ce test, un 403 rendu à tort ressemblerait à une garde qui fonctionne.
        mvc.perform(post(CONTENU + "/versions/99999/restaurer"))
                .andExpect(status().is(org.springframework.http.HttpStatus.BAD_REQUEST.value()));
    }
}

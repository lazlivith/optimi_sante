package com.optimisante.backend.domain.catalog;

import com.optimisante.backend.ConfigurationBaseDeTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ce que répond le catalogue pour une fiche qui n'existe pas.
 *
 * <p><b>Il répondait 400, et l'écran ne pouvait rien en faire.</b> Le dépôt levait un
 * {@code RuntimeException("Product not found")}, que le filet générique du gestionnaire
 * d'exceptions transformait en 400 — le même code qu'un intervalle de prix inversé ou qu'un
 * tri inconnu. Impossible, pour la fiche produit, de distinguer « cette adresse ne désigne
 * rien » de « votre requête est malformée », et donc d'afficher une page honnête.</p>
 *
 * <p><b>Un 404 écrit dans le code ne suffisait pas.</b> {@code ResponseStatusException} est
 * une {@code RuntimeException} : elle tombait dans ce même filet et ressortait en 400, code
 * demandé ou non. Le second test ci-dessous couvre ce piège, qui se serait rejoué à chaque
 * nouveau 404 du projet.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ConfigurationBaseDeTest.class)
@EnabledIf(value = "com.optimisante.backend.Docker#disponible",
           disabledReason = "Docker est indisponible : ce test a besoin d'un PostgreSQL éphémère.")
@WithAnonymousUser
class FicheIntrouvableTest {

    @Autowired
    private MockMvc mvc;

    @Test
    @DisplayName("une adresse qui ne désigne aucun produit répond 404")
    void adresseInconnue() throws Exception {
        mvc.perform(get("/api/v1/catalog/products/aucun-produit-a-cette-adresse")
                        .header("X-Tenant-Id", "FR_MAIN"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("le 404 n'est pas ramené à 400 par le filet générique")
    void pasRamenéÀQuatreCents() throws Exception {
        // La régression exacte qu'on corrige : le code voulu doit survivre au gestionnaire
        // d'exceptions. Sans le handler dédié, cette assertion échoue avec 400.
        mvc.perform(get("/api/v1/catalog/products/slug-sans-produit-derriere")
                        .header("X-Tenant-Id", "FR_MAIN"))
                .andExpect(status().is(404));
    }
}

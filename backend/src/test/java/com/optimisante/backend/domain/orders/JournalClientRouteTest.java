package com.optimisante.backend.domain.orders;

import com.optimisante.backend.ConfigurationBaseDeTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * La route par laquelle le navigateur signale ce que le serveur ne peut pas voir.
 *
 * <p><b>Pourquoi elle existe.</b> Une panne qui se produit dans le navigateur ne laisse aucune
 * trace côté serveur. Le 27 octobre, la clé publique Stripe était vide dans le paquet servi :
 * le paiement était impossible pour tout le monde, et la seule trace au monde était un message
 * dans la console de qui ouvrait les outils de développement.</p>
 *
 * <p><b>Pourquoi elle exige un compte.</b> Une route de journalisation ouverte permettrait à
 * n'importe qui de noyer nos journaux et d'y rendre tout le reste illisible. Les écrans qui en
 * ont besoin sont de toute façon derrière un compte. Ce test le verrouille, parce qu'un
 * {@code permitAll} ajouté par mégarde plus tard ne se verrait nulle part ailleurs.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ConfigurationBaseDeTest.class)
@EnabledIf(value = "com.optimisante.backend.Docker#disponible",
           disabledReason = "Docker est indisponible : ce test a besoin d'un PostgreSQL éphémère.")
class JournalClientRouteTest {

    @Autowired
    private MockMvc mvc;

    private static final String INCIDENT = """
            {"contexte":"paiement-carte","message":"Clé publique absente du paquet servi."}
            """;

    @Test
    @WithMockUser
    @DisplayName("un incident signalé par un visiteur connecté est accepté")
    void incidentAccepte() throws Exception {
        mvc.perform(post("/api/v1/journal-client")
                        .with(org.springframework.security.test.web.servlet.request
                                .SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(INCIDENT))
                // 204 : l'appelant est un écran en train de gérer une panne, il n'a rien à lire.
                .andExpect(status().isNoContent());
    }

    @Test
    @WithAnonymousUser
    @DisplayName("la route n'est pas ouverte : sans compte, rien n'entre dans nos journaux")
    void anonymeRefuse() throws Exception {
        mvc.perform(post("/api/v1/journal-client")
                        .with(org.springframework.security.test.web.servlet.request
                                .SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(INCIDENT))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    @DisplayName("un message démesuré est refusé plutôt que recopié dans les journaux")
    void messageBorne() throws Exception {
        // Sans borne, une pile d'appels entière — ou pire — partirait telle quelle dans les
        // journaux à chaque erreur d'un navigateur.
        String trop = "x".repeat(2000);
        mvc.perform(post("/api/v1/journal-client")
                        .with(org.springframework.security.test.web.servlet.request
                                .SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contexte\":\"paiement-carte\",\"message\":\"" + trop + "\"}"))
                .andExpect(status().isBadRequest());
    }
}

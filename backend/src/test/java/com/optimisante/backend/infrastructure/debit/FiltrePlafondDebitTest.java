package com.optimisante.backend.infrastructure.debit;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@DisplayName("Filtre de plafond de débit")
class FiltrePlafondDebitTest {

    private static final Clock HORLOGE_FIXE =
            Clock.fixed(Instant.parse("2026-11-12T10:00:00Z"), ZoneOffset.UTC);

    private FiltrePlafondDebit filtre(int parMinute, int global) {
        ReglagesPlafondDebit reglages = new ReglagesPlafondDebit(true, parMinute, 1_000, global);
        return new FiltrePlafondDebit(new PlafondDebit(reglages, HORLOGE_FIXE), reglages);
    }

    private MockHttpServletRequest requete(String chemin, String adresseTransmise) {
        MockHttpServletRequest requete = new MockHttpServletRequest("POST", chemin);
        requete.setRequestURI(chemin);
        requete.setRemoteAddr("172.16.0.1");
        if (adresseTransmise != null) {
            requete.addHeader("X-Forwarded-For", adresseTransmise);
        }
        return requete;
    }

    private MockHttpServletRequest requeteCloudflare(String chemin, String cfConnectingIp,
                                                     String xForwardedFor) {
        MockHttpServletRequest requete = requete(chemin, xForwardedFor);
        if (cfConnectingIp != null) {
            requete.addHeader("CF-Connecting-IP", cfConnectingIp);
        }
        return requete;
    }

    @Test
    @DisplayName("les quatre routes ouvertes sont plafonnées, et elles seules")
    void seulesLesRoutesOuvertesSontPlafonnees() throws Exception {
        FiltrePlafondDebit filtre = filtre(20, 1_000);

        for (String chemin : new String[]{
                "/api/v1/ai/chat",
                "/api/v1/ai/chat/stream",
                "/api/v1/ai/tools/catalog/search",
                "/api/v1/ai/tools/trainings"}) {
            assertThat(filtre.shouldNotFilter(requete(chemin, "1.2.3.4")))
                    .as("la route %s doit être plafonnée", chemin)
                    .isFalse();
        }

        for (String chemin : new String[]{
                "/api/v1/ai/extract",
                "/api/v1/ai/draft",
                "/api/v1/ai/status",
                "/api/v1/catalog/products",
                "/api/v1/auth/login"}) {
            assertThat(filtre.shouldNotFilter(requete(chemin, "1.2.3.4")))
                    .as("la route %s ne doit pas être plafonnée par ce lot", chemin)
                    .isTrue();
        }
    }

    @Test
    @DisplayName("sous le plafond, la requête poursuit sa route")
    void sousLePlafondLaRequetePoursuit() throws Exception {
        FiltrePlafondDebit filtre = filtre(5, 1_000);
        MockHttpServletResponse reponse = new MockHttpServletResponse();
        FilterChain chaine = mock(FilterChain.class);

        filtre.doFilter(requete("/api/v1/ai/chat", "1.2.3.4"), reponse, chaine);

        verify(chaine).doFilter(any(), any());
        assertThat(reponse.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("au delà du plafond : 429, Retry-After, message lisible, et le service n'est jamais appelé")
    void auDelaDuPlafond() throws Exception {
        FiltrePlafondDebit filtre = filtre(1, 1_000);

        // Première requête : elle passe et consomme le jeton unique.
        filtre.doFilter(requete("/api/v1/ai/chat", "1.2.3.4"),
                new MockHttpServletResponse(), new MockFilterChain());

        MockHttpServletResponse reponse = new MockHttpServletResponse();
        FilterChain chaine = mock(FilterChain.class);
        filtre.doFilter(requete("/api/v1/ai/chat", "1.2.3.4"), reponse, chaine);

        assertThat(reponse.getStatus()).isEqualTo(429);
        assertThat(reponse.getHeader("Retry-After")).isNotNull();
        assertThat(Integer.parseInt(reponse.getHeader("Retry-After"))).isPositive();
        assertThat(reponse.getContentAsString())
                .contains("Trop de messages")
                .contains("reessayerDansSecondes");
        verify(chaine, never())
                .doFilter(any(), any());
    }

    @Test
    @DisplayName("le message du plafond global ne révèle pas que c'est le budget qui est épuisé")
    void leMessageGlobalNeRevelePasLeBudget() throws Exception {
        FiltrePlafondDebit filtre = filtre(100, 1);

        filtre.doFilter(requete("/api/v1/ai/chat", "1.2.3.4"),
                new MockHttpServletResponse(), new MockFilterChain());

        MockHttpServletResponse reponse = new MockHttpServletResponse();
        filtre.doFilter(requete("/api/v1/ai/chat", "9.9.9.9"), reponse, new MockFilterChain());

        assertThat(reponse.getStatus()).isEqualTo(429);
        assertThat(reponse.getContentAsString())
                .as("annoncer le budget apprend combien d'appels suffisent à couper l'assistant")
                .doesNotContain("budget")
                .doesNotContain("plafond")
                .contains("momentanément indisponible");
    }

    @Test
    @DisplayName("derrière le proxy, c'est X-Forwarded-For qui distingue les visiteurs")
    void derriereLeProxyXForwardedForDistingueLesVisiteurs() throws Exception {
        FiltrePlafondDebit filtre = filtre(1, 1_000);

        // Deux visiteurs distincts, mais le MÊME getRemoteAddr() — la situation sur Render.
        MockHttpServletResponse premier = new MockHttpServletResponse();
        filtre.doFilter(requete("/api/v1/ai/chat", "1.2.3.4"), premier, new MockFilterChain());
        assertThat(premier.getStatus()).isEqualTo(200);

        MockHttpServletResponse second = new MockHttpServletResponse();
        filtre.doFilter(requete("/api/v1/ai/chat", "5.6.7.8"), second, new MockFilterChain());
        assertThat(second.getStatus())
                .as("sans lire X-Forwarded-For, le plafond par adresse serait un second plafond global")
                .isEqualTo(200);

        // Le même visiteur, lui, est bien plafonné.
        MockHttpServletResponse troisieme = new MockHttpServletResponse();
        filtre.doFilter(requete("/api/v1/ai/chat", "1.2.3.4"), troisieme, new MockFilterChain());
        assertThat(troisieme.getStatus()).isEqualTo(429);
    }

    @Test
    @DisplayName("une chaîne de proxys : c'est la DERNIÈRE entrée qui compte, pas la première")
    void chaineDeProxysSeuleLaDerniereCompte() throws Exception {
        FiltrePlafondDebit filtre = filtre(1, 1_000);

        // La partie gauche est ce que le client peut inventer ; la droite est ce qu'un proxy a
        // constaté. Deux requêtes dont seule la gauche change viennent du même endroit.
        filtre.doFilter(requete("/api/v1/ai/chat", "1.2.3.4, 203.0.113.7"),
                new MockHttpServletResponse(), new MockFilterChain());

        MockHttpServletResponse reponse = new MockHttpServletResponse();
        filtre.doFilter(requete("/api/v1/ai/chat", "9.9.9.9, 203.0.113.7"),
                reponse, new MockFilterChain());

        assertThat(reponse.getStatus())
                .as("inventer la première entrée ne doit pas rendre un crédit neuf — "
                        + "c'est exactement ce que faisait la première version")
                .isEqualTo(429);
    }

    @Test
    @DisplayName("les espaces et les entrées vides de la chaîne n'ouvrent pas de faille")
    void entreesVidesDansLaChaine() throws Exception {
        FiltrePlafondDebit filtre = filtre(1, 1_000);

        filtre.doFilter(requete("/api/v1/ai/chat", "  203.0.113.7  "),
                new MockHttpServletResponse(), new MockFilterChain());

        MockHttpServletResponse reponse = new MockHttpServletResponse();
        filtre.doFilter(requete("/api/v1/ai/chat", "1.1.1.1, 203.0.113.7,  , "),
                reponse, new MockFilterChain());

        assertThat(reponse.getStatus())
                .as("une entrée vide ajoutée en queue ne doit pas servir de compteur neuf")
                .isEqualTo(429);
    }

    @Test
    @DisplayName("CF-Connecting-IP est prioritaire sur X-Forwarded-For")
    void cloudflarePrimeSurXForwardedFor() throws Exception {
        FiltrePlafondDebit filtre = filtre(1, 1_000);

        // Même client vu par Cloudflare, mais un X-Forwarded-For entièrement différent :
        // le compteur doit rester le même, puisque c'est CF-Connecting-IP qui est lu.
        filtre.doFilter(requeteCloudflare("/api/v1/ai/chat", "198.51.100.42", "1.2.3.4, 10.0.0.1"),
                new MockHttpServletResponse(), new MockFilterChain());

        MockHttpServletResponse reponse = new MockHttpServletResponse();
        filtre.doFilter(requeteCloudflare("/api/v1/ai/chat", "198.51.100.42", "9.9.9.9, 10.9.9.9"),
                reponse, new MockFilterChain());

        assertThat(reponse.getStatus()).isEqualTo(429);
    }

    @Test
    @DisplayName("deux clients Cloudflare distincts gardent chacun leur crédit")
    void deuxClientsCloudflareDistincts() throws Exception {
        FiltrePlafondDebit filtre = filtre(1, 1_000);

        MockHttpServletResponse premier = new MockHttpServletResponse();
        filtre.doFilter(requeteCloudflare("/api/v1/ai/chat", "198.51.100.42", null),
                premier, new MockFilterChain());
        assertThat(premier.getStatus()).isEqualTo(200);

        MockHttpServletResponse second = new MockHttpServletResponse();
        filtre.doFilter(requeteCloudflare("/api/v1/ai/chat", "198.51.100.43", null),
                second, new MockFilterChain());
        assertThat(second.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("sans aucun en-tête, on retombe sur getRemoteAddr — juste en développement local")
    void repliSurGetRemoteAddr() throws Exception {
        FiltrePlafondDebit filtre = filtre(1, 1_000);

        filtre.doFilter(requete("/api/v1/ai/chat", null),
                new MockHttpServletResponse(), new MockFilterChain());

        MockHttpServletResponse reponse = new MockHttpServletResponse();
        filtre.doFilter(requete("/api/v1/ai/chat", null), reponse, new MockFilterChain());

        assertThat(reponse.getStatus())
                .as("sans en-tête, les deux requêtes viennent de la même adresse directe")
                .isEqualTo(429);
    }

    @Test
    @DisplayName("un en-tête démesuré ne devient pas une clé démesurée")
    void enTeteDemesureEstBorne() throws Exception {
        FiltrePlafondDebit filtre = filtre(100, 1_000);
        String enorme = "9".repeat(5_000);

        MockHttpServletResponse reponse = new MockHttpServletResponse();
        filtre.doFilter(requeteCloudflare("/api/v1/ai/chat", enorme, null),
                reponse, new MockFilterChain());

        assertThat(reponse.getStatus())
                .as("la requête passe, mais la clé de carte est tronquée à 64 caractères")
                .isEqualTo(200);
    }

    @Test
    @DisplayName("désactivé par configuration, le filtre ne s'applique plus")
    void desactiveParConfiguration() {
        ReglagesPlafondDebit reglages = new ReglagesPlafondDebit(false, 1, 1, 1);
        FiltrePlafondDebit filtre =
                new FiltrePlafondDebit(new PlafondDebit(reglages, HORLOGE_FIXE), reglages);

        assertThat(filtre.shouldNotFilter(requete("/api/v1/ai/chat", "1.2.3.4")))
                .as("un plafond mal réglé doit se neutraliser par configuration, sans redéploiement")
                .isTrue();
    }
}

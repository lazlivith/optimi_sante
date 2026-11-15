package com.optimisante.backend.infrastructure.debit;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class FiltreDepotPartenariatTest {

    private static final Clock HORLOGE_FIXE =
            Clock.fixed(Instant.parse("2026-11-15T10:00:00Z"), ZoneOffset.UTC);

    private static final String DEPOT = "/api/v1/partnership/requests";
    private static final String RELECTURE = "/api/v1/partnership/dossier/verification";

    private static FiltreDepotPartenariat filtre(boolean actif, int parMinute, int global) {
        return new FiltreDepotPartenariat(new GardeDepotPartenariat(
                new ReglagesDepotPartenariat(actif, parMinute, 1_000, global, null, null, null),
                HORLOGE_FIXE));
    }

    /** Une requête multipart dont la taille annoncée est celle qu'on veut, sans corps réel. */
    private static MockHttpServletRequest requete(String methode, String chemin, long tailleAnnoncee) {
        MockHttpServletRequest requete = new MockHttpServletRequest(methode, chemin) {
            @Override
            public long getContentLengthLong() {
                return tailleAnnoncee;
            }
        };
        requete.addHeader("CF-Connecting-IP", "203.0.113.7");
        return requete;
    }

    @Test
    @DisplayName("un dépôt de taille ordinaire passe, sur les deux routes")
    void depotOrdinaire() throws Exception {
        FiltreDepotPartenariat filtre = filtre(true, 10, 300);
        for (String chemin : new String[]{DEPOT, RELECTURE}) {
            FilterChain chaine = mock(FilterChain.class);
            MockHttpServletResponse reponse = new MockHttpServletResponse();

            filtre.doFilter(requete("POST", chemin, 3_000_000), reponse, chaine);

            assertThat(reponse.getStatus()).as(chemin).isEqualTo(200);
            verify(chaine).doFilter(any(), any());
        }
    }

    @Test
    @DisplayName("55 Mo annoncés : refus 413 avant lecture, la suite de la chaîne n'est jamais appelée")
    void requeteTropGrosseRefuseeSurSonAnnonce() throws Exception {
        FiltreDepotPartenariat filtre = filtre(true, 10, 300);
        FilterChain chaine = mock(FilterChain.class);
        MockHttpServletResponse reponse = new MockHttpServletResponse();

        filtre.doFilter(requete("POST", DEPOT, 55L * 1024 * 1024), reponse, chaine);

        assertThat(reponse.getStatus()).isEqualTo(413);
        assertThat(reponse.getContentAsString()).contains("\"message\"").contains("10 Mo");
        verify(chaine, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("un refus pour la taille ne consomme pas le quota de l'établissement")
    void refusDeTailleNeDebitePas() throws Exception {
        FiltreDepotPartenariat filtre = filtre(true, 2, 300);
        for (int i = 0; i < 20; i++) {
            filtre.doFilter(requete("POST", DEPOT, 55L * 1024 * 1024),
                    new MockHttpServletResponse(), mock(FilterChain.class));
        }

        FilterChain chaine = mock(FilterChain.class);
        filtre.doFilter(requete("POST", DEPOT, 1_000), new MockHttpServletResponse(), chaine);
        filtre.doFilter(requete("POST", DEPOT, 1_000), new MockHttpServletResponse(), chaine);
        verify(chaine, times(2)).doFilter(any(), any());
    }

    @Test
    @DisplayName("au-delà du plafond par minute : 429, Retry-After, et un message lisible")
    void plafondParMinute() throws Exception {
        FiltreDepotPartenariat filtre = filtre(true, 3, 300);
        for (int i = 0; i < 3; i++) {
            filtre.doFilter(requete("POST", DEPOT, 1_000), new MockHttpServletResponse(), mock(FilterChain.class));
        }

        FilterChain chaine = mock(FilterChain.class);
        MockHttpServletResponse reponse = new MockHttpServletResponse();
        filtre.doFilter(requete("POST", RELECTURE, 1_000), reponse, chaine);

        assertThat(reponse.getStatus()).isEqualTo(429);
        assertThat(reponse.getHeader("Retry-After")).isNotBlank();
        assertThat(reponse.getContentAsString()).contains("Trop d'envois");
        verify(chaine, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("la coupure globale ne révèle pas qu'un quota commun est épuisé")
    void coupureGlobaleDiscrete() throws Exception {
        FiltreDepotPartenariat filtre = filtre(true, 100, 1);
        filtre.doFilter(requete("POST", DEPOT, 1_000), new MockHttpServletResponse(), mock(FilterChain.class));

        MockHttpServletResponse reponse = new MockHttpServletResponse();
        filtre.doFilter(requete("POST", DEPOT, 1_000), reponse, mock(FilterChain.class));

        assertThat(reponse.getStatus()).isEqualTo(429);
        assertThat(reponse.getContentAsString())
                .contains("momentanément indisponible")
                .doesNotContainIgnoringCase("quota")
                .doesNotContainIgnoringCase("plafond");
    }

    @Test
    @DisplayName("les téléchargements de modèles et les autres routes ne sont ni comptés ni refusés")
    void routesHorsDepot() throws Exception {
        FiltreDepotPartenariat filtre = filtre(true, 1, 1);
        String[][] requetes = {
                {"GET", "/api/v1/partnership/dossier-modele"},
                {"GET", "/api/v1/partnership/convention-modele"},
                {"GET", "/api/v1/partnership/formulaire"},
                {"GET", DEPOT},
                {"POST", "/api/v1/admin/partnership/requests"},
                {"POST", "/api/v1/enrollments/documents"}};
        for (int tour = 0; tour < 3; tour++) {
            for (String[] r : requetes) {
                FilterChain chaine = mock(FilterChain.class);
                MockHttpServletResponse reponse = new MockHttpServletResponse();
                filtre.doFilter(requete(r[0], r[1], 55L * 1024 * 1024), reponse, chaine);
                assertThat(reponse.getStatus()).as(r[0] + " " + r[1]).isEqualTo(200);
                verify(chaine).doFilter(any(), any());
            }
        }
    }

    @Test
    @DisplayName("garde désactivée : plus de 429, mais la requête trop grosse reste refusée")
    void gardeDesactivee() throws Exception {
        FiltreDepotPartenariat filtre = filtre(false, 1, 1);
        for (int i = 0; i < 5; i++) {
            FilterChain chaine = mock(FilterChain.class);
            filtre.doFilter(requete("POST", DEPOT, 1_000), new MockHttpServletResponse(), chaine);
            verify(chaine).doFilter(any(), any());
        }

        MockHttpServletResponse reponse = new MockHttpServletResponse();
        filtre.doFilter(requete("POST", DEPOT, 55L * 1024 * 1024), reponse, mock(FilterChain.class));
        assertThat(reponse.getStatus()).isEqualTo(413);
    }
}

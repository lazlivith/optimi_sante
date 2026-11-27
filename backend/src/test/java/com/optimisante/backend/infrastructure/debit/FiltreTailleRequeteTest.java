package com.optimisante.backend.infrastructure.debit;

import com.optimisante.backend.common.storage.ControleFichier;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class FiltreTailleRequeteTest {

    private static final long MO = 1024L * 1024;

    private final FiltreTailleRequete filtre = new FiltreTailleRequete();

    private static MockHttpServletRequest requete(String methode, String chemin, long tailleAnnoncee) {
        return new MockHttpServletRequest(methode, chemin) {
            @Override
            public long getContentLengthLong() {
                return tailleAnnoncee;
            }
        };
    }

    private int statut(String methode, String chemin, long taille) throws Exception {
        MockHttpServletResponse reponse = new MockHttpServletResponse();
        filtre.doFilter(requete(methode, chemin, taille), reponse, mock(FilterChain.class));
        return reponse.getStatus();
    }

    @Test
    @DisplayName("par défaut : 11 Mo passent, un octet de plus est refusé en 413 sans appeler la suite")
    void defaut() throws Exception {
        assertThat(statut("POST", "/api/v1/enrollments/abc/documents", 11 * MO)).isEqualTo(200);

        FilterChain chaine = mock(FilterChain.class);
        MockHttpServletResponse reponse = new MockHttpServletResponse();
        filtre.doFilter(requete("POST", "/api/v1/enrollments/abc/documents", 11 * MO + 1), reponse, chaine);

        assertThat(reponse.getStatus()).isEqualTo(413);
        assertThat(reponse.getContentAsString()).contains("\"message\"").contains("10 Mo");
        verify(chaine, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("une route que personne n'a encore écrite est protégée d'office")
    void routeInconnue() throws Exception {
        assertThat(statut("POST", "/api/v1/une-route/qui-n-existe-pas-encore", 55 * MO)).isEqualTo(413);
        assertThat(statut("PUT", "/api/v1/autre/chose", 12 * MO)).isEqualTo(413);
    }

    @Test
    @DisplayName("les quatre dérogations reçoivent ce que leur usage justifie, et pas davantage")
    void derogations() throws Exception {
        String video = "/api/v1/admin/catalog/products/7f3c/video";
        String videoFormation = "/api/v1/partner/trainings/7f3c/video";
        String galerie = "/api/v1/admin/catalog/products/7f3c/gallery";
        String catalogue = "/api/v1/admin/suppliers/7f3c/imports";

        assertThat(statut("POST", video, 55 * MO)).isEqualTo(200);
        assertThat(statut("POST", video, 55 * MO + 1)).isEqualTo(413);
        assertThat(statut("POST", videoFormation, 55 * MO)).isEqualTo(200);
        assertThat(statut("POST", videoFormation, 56 * MO)).isEqualTo(413);
        assertThat(statut("POST", galerie, 120 * MO)).isEqualTo(200);
        assertThat(statut("POST", galerie, 121 * MO)).isEqualTo(413);
        assertThat(statut("POST", catalogue, 16 * MO)).isEqualTo(200);
        assertThat(statut("POST", catalogue, 17 * MO)).isEqualTo(413);
    }

    @Test
    @DisplayName("une dérogation vaut pour sa route exacte et sa méthode, pas pour ses voisines")
    void derogationStricte() throws Exception {
        // L'image d'une formation, la vidéo d'un autre préfixe, un segment de plus : le défaut.
        for (String chemin : new String[]{
                "/api/v1/partner/trainings/7f3c/image",
                "/api/v1/partner/trainings/7f3c/video/extra",
                "/api/v1/admin/catalog/products/7f3c/a/video",
                "/api/v1/admin/catalog/products/7f3c/media",
                "/api/v1/video"}) {
            assertThat(statut("POST", chemin, 30 * MO)).as(chemin).isEqualTo(413);
        }
        assertThat(statut("PUT", "/api/v1/admin/catalog/products/7f3c/video", 30 * MO)).isEqualTo(413);
    }

    @Test
    @DisplayName("sans corps ou sans taille annoncée, la requête passe : lectures et envois par morceaux")
    void sansTailleAnnoncee() throws Exception {
        assertThat(statut("GET", "/api/v1/trainings", -1)).isEqualTo(200);
        assertThat(statut("GET", "/api/v1/trainings", 0)).isEqualTo(200);
        // Limite connue : un envoi par morceaux n'annonce rien, et n'est borné que plus loin.
        assertThat(statut("POST", "/api/v1/enrollments/abc/documents", -1)).isEqualTo(200);
    }

    @Test
    @DisplayName("verifierTaille borne la taille et ne juge rien d'autre")
    void tailleSeule() {
        MockMultipartFile leger = new MockMultipartFile("file", "scan.tiff", "image/tiff", new byte[2_000]);
        MockMultipartFile lourd = new MockMultipartFile("file", "scan.pdf", "application/pdf",
                new byte[(int) ControleFichier.TAILLE_MAX_OCTETS + 1]);

        // Un format que verifierDocument refuserait passe ici : seule la taille est jugée.
        assertThatCode(() -> ControleFichier.verifierTaille(leger, ControleFichier.TAILLE_MAX_OCTETS, "La pièce"))
                .doesNotThrowAnyException();
        assertThatCode(() -> ControleFichier.verifierTaille(null, ControleFichier.TAILLE_MAX_OCTETS, "La pièce"))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> ControleFichier.verifierTaille(lourd, ControleFichier.TAILLE_MAX_OCTETS, "La pièce"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("La pièce dépasse 10 Mo.");
    }
}

package com.optimisante.backend.domain.document;

import com.optimisante.backend.common.storage.DossierStockage;
import com.optimisante.backend.common.storage.StorageService;
import com.optimisante.backend.config.exception.GlobalExceptionHandler;
import com.optimisante.backend.config.exception.ReferencePerimeeException;
import com.optimisante.backend.domain.document.controller.StorageController;
import com.optimisante.backend.domain.document.service.DocumentAccessTokenService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * La fenêtre de déploiement de DP-4 : ce qui tient la page ancienne, et ce qui refuse une clé.
 *
 * <p><b>Pourquoi ce test.</b> Les deux ordres de déploiement cassaient le dépôt des pièces, et en
 * silence : la page lisait un champ absent, rangeait {@code undefined}, et le bouton restait
 * désactivé sans message. L'alias de transition {@code publicId} répare cela — mais il ne vaut
 * que s'il porte la <b>référence</b> et non la clé. C'est précisément ce qu'on verrouille ici,
 * parce qu'un alias mal rempli serait exactement la fuite que le lot ferme.</p>
 */
@DisplayName("Fenêtre de déploiement de DP-4 : alias de transition et refus d'une clé")
class TransitionDepotPieceTest {

    private static final String SECRET = "secret-de-test-pour-les-jetons-de-document-0123456789";
    private static final String CLE =
            "optimisante/prod/dossiers-candidats/pieces/3f2a9c7e-passeport";

    private final DocumentAccessTokenService jetons = new DocumentAccessTokenService(SECRET);

    private Map<String, String> deposer() {
        StorageService stockage = mock(StorageService.class);
        when(stockage.uploadFile(any(), any(DossierStockage.class))).thenReturn(CLE);

        StorageController controleur = new StorageController(stockage, jetons);
        MockMultipartFile fichier = new MockMultipartFile(
                "file", "passeport.pdf", "application/pdf",
                "%PDF-1.4 contenu de test".getBytes());

        ResponseEntity<Map<String, String>> reponse =
                controleur.uploadFile(fichier, "DOSSIERS_PIECES");
        assertThat(reponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        return reponse.getBody();
    }

    @Test
    @DisplayName("l'alias publicId porte la référence, jamais la clé de stockage")
    void aliasPorteLaReference() {
        Map<String, String> corps = deposer();

        assertThat(corps).containsKeys("reference", "previewUrl", "publicId", "message");
        assertThat(corps.get("publicId"))
                .as("l'alias existe pour la page ancienne, mais sa VALEUR est la référence : "
                        + "sans quoi il rouvrirait la fuite que DP-4 ferme")
                .isEqualTo(corps.get("reference"))
                .isNotEqualTo(CLE)
                .doesNotContain("dossiers-candidats");
    }

    @Test
    @DisplayName("la référence rendue désigne bien le fichier déposé")
    void laReferenceDesigneLeFichier() {
        String reference = deposer().get("reference");

        assertThat(jetons.verifier(reference).orElseThrow().publicId()).isEqualTo(CLE);
    }

    @Test
    @DisplayName("aucun champ de la réponse ne contient la clé")
    void aucunChampNeContientLaCle() {
        assertThat(deposer().values())
                .as("ni la référence, ni l'aperçu, ni l'alias, ni le message")
                .noneMatch(valeur -> valeur != null && valeur.contains("dossiers-candidats"));
    }

    @Test
    @DisplayName("une clé présentée par un client est refusée en 409, avec un code")
    void uneCleEstRefusee() {
        // Ce que renvoie une page restée ouverte depuis avant le déploiement : une vraie clé.
        assertThat(jetons.verifier(CLE))
                .as("une clé de stockage n'est pas une référence et ne doit jamais en tenir lieu")
                .isEmpty();

        ResponseEntity<Map<String, String>> reponse = new GlobalExceptionHandler()
                .handleReferencePerimee(new ReferencePerimeeException());

        assertThat(reponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(reponse.getBody())
                .containsEntry("code", "REFERENCE_PERIMEE")
                .containsEntry("message",
                        "La page a été mise à jour, merci de la recharger avant de déposer vos pièces.");
    }
}

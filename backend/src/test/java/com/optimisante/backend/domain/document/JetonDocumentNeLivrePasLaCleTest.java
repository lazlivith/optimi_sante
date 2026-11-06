package com.optimisante.backend.domain.document;

import com.optimisante.backend.domain.document.service.DocumentAccessTokenService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Le jeton de document ne doit pas laisser lire la clé de stockage qu'il désigne.
 *
 * <p><b>Pourquoi ce test existe.</b> DP-4 retire les clés des réponses JSON et les remplace par
 * une « référence signée ». Mais la charge du jeton était seulement <em>encodée en base64</em> :
 * un {@code atob(jeton.split('.')[0])} rendait la clé en clair. La fuite que DP-4 fermait d'un
 * côté restait donc ouverte de l'autre, et personne ne l'avait regardée. Ce test verrouille le
 * fait que la charge est désormais <b>chiffrée</b>.</p>
 */
@DisplayName("Le jeton de document ne livre pas la clé de stockage")
class JetonDocumentNeLivrePasLaCleTest {

    /** Suffisamment long pour une clé HMAC, et sans rapport avec un secret réel. */
    private static final String SECRET = "secret-de-test-pour-les-jetons-de-document-0123456789";

    private static final String CLE = "optimisante/prod/dossiers-candidats/pieces/"
            + "3f2a9c7e-1b44-4d8f-9a01-2c5e7b8d9f00_passeport";

    private final DocumentAccessTokenService service = new DocumentAccessTokenService(SECRET);

    @Test
    @DisplayName("un jeton délivré se vérifie et rend la clé")
    void allerRetour() {
        String jeton = service.delivrer(CLE, 60, "passeport.pdf");

        assertThat(service.verifier(jeton))
                .as("un jeton que nous venons de délivrer doit se vérifier")
                .isPresent();
        assertThat(service.verifier(jeton).orElseThrow().publicId()).isEqualTo(CLE);
    }

    @Test
    @DisplayName("la clé n'apparaît ni dans le jeton, ni dans son décodage base64")
    void laCleNestPasLisible() {
        String jeton = service.delivrer(CLE, 60, "passeport.pdf");

        assertThat(jeton)
                .as("le jeton lui-même ne doit contenir aucun fragment de la clé")
                .doesNotContain(CLE)
                .doesNotContain("dossiers-candidats")
                .doesNotContain("passeport");

        // Ce qu'un visiteur ferait dans la console de son navigateur.
        String charge = jeton.substring(0, jeton.lastIndexOf('.'));
        byte[] decode = Base64.getUrlDecoder().decode(charge);
        String lisible = new String(decode, StandardCharsets.UTF_8);

        assertThat(lisible)
                .as("décoder la charge en base64 ne doit rien rendre d'exploitable : "
                        + "c'est exactement ce qui livrait la clé avant ce correctif")
                .doesNotContain("dossiers-candidats")
                .doesNotContain("optimisante/")
                .doesNotContain(CLE);
    }

    @Test
    @DisplayName("deux jetons du même fichier ne se ressemblent pas")
    void deuxJetonsDifferent() {
        String premier = service.delivrer(CLE, 60, null);
        String second = service.delivrer(CLE, 60, null);

        // Sans vecteur d'initialisation aléatoire, deux liens vers la même pièce seraient
        // identiques : on pourrait reconnaître qu'ils désignent le même fichier sans le lire.
        assertThat(premier).isNotEqualTo(second);
        assertThat(service.verifier(premier)).isPresent();
        assertThat(service.verifier(second)).isPresent();
    }

    @Test
    @DisplayName("un jeton dont la charge est remplacée est refusé")
    void chargeAlteree() {
        String jeton = service.delivrer(CLE, 60, null);
        String signature = jeton.substring(jeton.lastIndexOf('.') + 1);

        // Une charge fabriquée à l'ancien format (base64 en clair), avec la signature d'un vrai
        // jeton : c'est la tentative la plus évidente.
        String fabriquee = Base64.getUrlEncoder().withoutPadding().encodeToString(
                (CLE + "|9999999999|").getBytes(StandardCharsets.UTF_8));

        assertThat(service.verifier(fabriquee + "." + signature)).isEmpty();
    }

    @Test
    @DisplayName("un jeton signé avec un autre secret est refusé")
    void autreSecret() {
        String jetonEtranger = new DocumentAccessTokenService(SECRET + "-different")
                .delivrer(CLE, 60, null);

        assertThat(service.verifier(jetonEtranger)).isEmpty();
    }

    @Test
    @DisplayName("un jeton expiré est refusé")
    void expire() {
        assertThat(service.verifier(service.delivrer(CLE, -1, null))).isEmpty();
    }
}

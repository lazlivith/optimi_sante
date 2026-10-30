package com.optimisante.backend.common.storage.cloudinary;

import com.cloudinary.Cloudinary;
import com.optimisante.backend.common.storage.DossierStockage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Vérifie, sur le VRAI compte Cloudinary, qu'un document déposé en {@code authenticated} :
 * <ol>
 *   <li>n'est plus lisible anonymement (l'URL publique {@code raw/upload} ne le sert pas) ;</li>
 *   <li>reste lisible par {@link CloudinaryStorageService#download} (qui signe en authenticated).</li>
 * </ol>
 *
 * <p><b>Pourquoi ce test existe, et pourquoi il est gardé.</b> C'est la condition qui autorise le
 * déploiement de DP-1. Si la livraison {@code authenticated} d'un {@code raw} sans extension ne
 * fonctionnait pas sur l'offre gratuite — notamment à cause du réglage « PDF and ZIP files
 * delivery » —, alors les NOUVEAUX dépôts (authenticated) deviendraient illisibles, et le repli
 * {@code upload} ne les sauverait pas (un objet authenticated ne se sert pas en upload). La
 * documentation Cloudinary confirme que les URL signées authenticated fonctionnent sur tous les
 * comptes ; ce test le confirme sur LE nôtre, avec un vrai fichier.</p>
 *
 * <p>Gardé par la présence de {@code CLOUDINARY_API_SECRET} dans l'environnement : sans elle, le
 * test est <b>ignoré</b> (il apparaît dans le nombre d'ignorés), jamais en échec. Il écrit sur
 * Cloudinary — à lancer en développement, identifiants exportés, jamais en production. Il
 * nettoie derrière lui.</p>
 */
@DisplayName("Aller-retour authenticated sur le vrai compte Cloudinary (DP-1)")
@EnabledIfEnvironmentVariable(named = "CLOUDINARY_API_SECRET", matches = ".+")
class CloudinaryAuthenticatedRoundTripTest {

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private CloudinaryStorageService service() {
        String cloudName = System.getenv("CLOUDINARY_CLOUD_NAME");
        Cloudinary cloudinary = new Cloudinary(Map.of(
                "cloud_name", cloudName,
                "api_key", System.getenv("CLOUDINARY_API_KEY"),
                "api_secret", System.getenv("CLOUDINARY_API_SECRET")));
        // Dossier de test isolé, pour ne pas se mêler aux vrais documents.
        ArborescenceCloudinary arborescence = new ArborescenceCloudinary("optimisante", "test-dp1");
        return new CloudinaryStorageService(cloudinary, arborescence);
    }

    @Test
    @DisplayName("un document déposé est authenticated : anonyme refusé, download() lit")
    void allerRetourAuthenticated() throws Exception {
        CloudinaryStorageService service = service();
        byte[] contenu = ("%PDF-1.4 test DP-1 " + System.nanoTime()).getBytes(StandardCharsets.UTF_8);

        // DOSSIERS_PIECES est un dossier DOCUMENT : le dépôt doit être authenticated.
        String publicId = service.uploadFile(contenu, "test-dp1-roundtrip", DossierStockage.DOSSIERS_PIECES);
        try {
            // 1. L'URL publique raw/upload NE DOIT PAS servir le fichier.
            String cloudName = System.getenv("CLOUDINARY_CLOUD_NAME");
            String urlPublique = "https://res.cloudinary.com/" + cloudName + "/raw/upload/" + publicId;
            HttpResponse<byte[]> anonyme = HTTP.send(
                    HttpRequest.newBuilder(URI.create(urlPublique)).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            assertThat(anonyme.statusCode())
                    .as("un objet authenticated ne doit pas se servir en raw/upload anonyme")
                    .isNotEqualTo(200);

            // 2. download() le lit (il signe en authenticated).
            byte[] relu = service.download(publicId);
            assertThat(relu).isEqualTo(contenu);
        } finally {
            service.deleteDocument(publicId);
        }
    }
}

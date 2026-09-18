package com.optimisante.backend.domain.catalog.supplier;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

/**
 * Récupère le visuel d'un produit depuis l'adresse fournie dans le fichier catalogue.
 *
 * <p><b>Ce que le serveur refuse de rapporter.</b> Une réponse qui n'est pas une image, un fichier
 * de plus de 8 Mo, une adresse qui ne répond pas en quinze secondes : le produit est alors créé
 * sans visuel et la ligne le signale. Télécharger deux mille fichiers inconnus impose ces limites —
 * sans elles, un seul lien mal formé bloque l'import entier.</p>
 */
@Slf4j
@Component
public class TelechargeurImage {

    static final long TAILLE_MAX_OCTETS = 8L * 1024 * 1024;

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public record Image(byte[] contenu, String nomFichier) {
    }

    /** @return l'image, ou vide avec le motif journalisé — jamais d'exception vers l'appelant */
    public Optional<Image> telecharger(String url) {
        try {
            HttpRequest requete = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("User-Agent", "OptimiSante-Import/1.0")
                    .GET().build();
            HttpResponse<byte[]> reponse = CLIENT.send(requete, HttpResponse.BodyHandlers.ofByteArray());
            if (reponse.statusCode() != 200) {
                log.debug("Image {} : réponse {}", url, reponse.statusCode());
                return Optional.empty();
            }
            String type = reponse.headers().firstValue("content-type").orElse("");
            if (!type.startsWith("image/")) {
                log.debug("Image {} : type {} inattendu", url, type);
                return Optional.empty();
            }
            byte[] contenu = reponse.body();
            if (contenu.length == 0 || contenu.length > TAILLE_MAX_OCTETS) {
                log.debug("Image {} : {} octets", url, contenu.length);
                return Optional.empty();
            }
            return Optional.of(new Image(contenu, nomDepuisUrl(url)));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (Exception e) {
            log.debug("Image {} inaccessible : {}", url, e.getMessage());
            return Optional.empty();
        }
    }

    private static String nomDepuisUrl(String url) {
        String chemin = URI.create(url).getPath();
        int barre = chemin.lastIndexOf('/');
        String nom = barre >= 0 ? chemin.substring(barre + 1) : chemin;
        return nom.isBlank() ? "image" : nom;
    }
}

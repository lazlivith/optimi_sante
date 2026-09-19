package com.optimisante.backend.domain.catalog.supplier;

import com.optimisante.backend.common.security.AdresseDistanteAutorisee;
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
 *
 * <p><b>Où le serveur accepte d'aller.</b> L'adresse, et chacune de ses redirections, passe par
 * {@link AdresseDistanteAutorisee} : le fichier vient d'un fournisseur, et c'est le serveur qui
 * appelle. Sans ce contrôle, une ligne du catalogue ferait de lui un relais vers le réseau
 * interne.</p>
 */
@Slf4j
@Component
public class TelechargeurImage {

    static final long TAILLE_MAX_OCTETS = 8L * 1024 * 1024;

    /** Trois sauts suffisent aux redirections légitimes d'un hébergeur d'images. */
    private static final int REDIRECTIONS_MAX = 3;

    // Les redirections sont suivies à la main : chaque saut doit repasser le contrôle d'adresse,
    // sans quoi une adresse publique renverrait vers le réseau interne en un seul rebond.
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    public record Image(byte[] contenu, String nomFichier) {
    }

    /** @return l'image, ou vide avec le motif journalisé — jamais d'exception vers l'appelant */
    public Optional<Image> telecharger(String url) {
        try {
            HttpResponse<byte[]> reponse = appeler(URI.create(url));
            if (reponse == null) {
                return Optional.empty();
            }
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

    /**
     * Appelle l'adresse en suivant ses redirections, chacune contrôlée à son tour.
     *
     * @return la réponse finale, ou {@code null} si une adresse a été refusée ou les sauts épuisés
     */
    private HttpResponse<byte[]> appeler(URI adresse) throws Exception {
        for (int saut = 0; saut <= REDIRECTIONS_MAX; saut++) {
            Optional<String> refus = AdresseDistanteAutorisee.motifDeRefus(adresse);
            if (refus.isPresent()) {
                log.warn("Image non récupérée — {} : {}", adresse, refus.get());
                return null;
            }
            HttpRequest requete = HttpRequest.newBuilder(adresse)
                    .timeout(Duration.ofSeconds(15))
                    .header("User-Agent", "OptimiSante-Import/1.0")
                    .GET().build();
            HttpResponse<byte[]> reponse = CLIENT.send(requete, HttpResponse.BodyHandlers.ofByteArray());
            int code = reponse.statusCode();
            if (code < 300 || code > 399) {
                return reponse;
            }
            Optional<String> suivante = reponse.headers().firstValue("location");
            if (suivante.isEmpty()) {
                log.debug("Image {} : redirection {} sans destination", adresse, code);
                return reponse;
            }
            adresse = adresse.resolve(suivante.get());
        }
        log.debug("Image {} : plus de {} redirections", adresse, REDIRECTIONS_MAX);
        return null;
    }

    private static String nomDepuisUrl(String url) {
        String chemin = URI.create(url).getPath();
        int barre = chemin.lastIndexOf('/');
        String nom = barre >= 0 ? chemin.substring(barre + 1) : chemin;
        return nom.isBlank() ? "image" : nom;
    }
}

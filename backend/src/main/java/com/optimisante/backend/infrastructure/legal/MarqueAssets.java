package com.optimisante.backend.infrastructure.legal;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.util.Base64;

/**
 * Le logo, sous la forme que le moteur PDF sait consommer.
 *
 * <p><b>Pourquoi une adresse « data: » plutôt qu'un chemin de fichier.</b> Le rendu PDF part
 * d'une chaîne HTML sans adresse de base : une image référencée par un chemin relatif ne se
 * résout donc pas. Et en production l'application vit dans un {@code .jar} — une ressource du
 * classpath n'y est pas un fichier, ce qui exclut aussi le chemin absolu. Les octets sont donc
 * lus une fois au démarrage et encodés en base64.</p>
 *
 * <p>Le coût est assumé : le logo pèse quelques kilo-octets, son encodage un tiers de plus, et
 * il s'ajoute à chaque document. En échange, un reçu envoyé à un client porte enfin la marque
 * au lieu d'un simple mot écrit en vert.</p>
 *
 * <p>Chargé une seule fois : relire le fichier à chaque document ferait un accès disque par
 * reçu, pour un contenu qui ne change jamais.</p>
 */
@Slf4j
@Component
public class MarqueAssets {

    /** Version sombre : les documents sont imprimés sur du papier blanc. */
    private static final String CHEMIN = "static/marque/optimi-logotype-pdf.png";

    private String logoDataUri = "";

    @PostConstruct
    void charger() {
        try {
            byte[] octets = new ClassPathResource(CHEMIN).getContentAsByteArray();
            logoDataUri = "data:image/png;base64," + Base64.getEncoder().encodeToString(octets);
            log.info("Logo des documents chargé ({} Ko encodés).", logoDataUri.length() / 1024);
        } catch (Exception e) {
            // Volontairement non bloquant : un document sans logo reste un document valable,
            // alors qu'un serveur qui refuse de démarrer pour une image ne l'est pas. Le
            // gabarit retombe sur le nom écrit en toutes lettres.
            log.warn("Logo des documents introuvable ({}) : les PDF porteront le nom en texte.",
                    CHEMIN);
        }
    }

    /** Adresse embarquée du logo, ou chaîne vide si le fichier manque. */
    public String logoDataUri() {
        return logoDataUri;
    }

    /** Le gabarit s'en sert pour choisir entre l'image et le repli textuel. */
    public boolean logoDisponible() {
        return !logoDataUri.isEmpty();
    }
}

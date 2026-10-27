package com.optimisante.backend.config.exception;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ce que le navigateur n'arrive pas à faire, et que personne ne verrait sans cela.
 *
 * <p><b>Pourquoi cette route existe.</b> Une panne qui se produit dans le navigateur ne laisse
 * aucune trace côté serveur. Le 27 octobre, la clé publique Stripe était vide dans le paquet
 * servi : le paiement était impossible pour tout le monde, et la seule trace au monde était un
 * message dans la console de qui ouvrait les outils de développement. Aucun journal, aucune
 * alerte — la panne a été trouvée parce qu'un humain a regardé.</p>
 *
 * <p><b>Pourquoi elle exige d'être connecté.</b> Une route de journalisation ouverte est une
 * invitation à noyer nos journaux : n'importe qui pourrait en écrire autant qu'il veut, et
 * rendre illisible tout le reste. Les écrans qui en ont besoin — le paiement en premier — sont
 * de toute façon derrière un compte. Aucune règle n'est à ajouter dans la configuration de
 * sécurité : {@code anyRequest().authenticated()} s'en charge.</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/journal-client")
@Validated
public class JournalClientResource {

    /**
     * Un incident vu par le navigateur.
     *
     * @param contexte où cela s'est produit, en quelques mots ({@code paiement-carte})
     * @param message  ce qui s'est passé, borné pour qu'une pile d'appels entière ne parte pas
     *                 dans les journaux
     */
    public record Incident(
            @NotBlank @Size(max = 60) String contexte,
            @NotBlank @Size(max = 500) String message) {
    }

    /**
     * Répond 204 : l'appelant est un écran en train de gérer une panne, il n'a rien à lire et
     * ne doit surtout pas échouer une seconde fois sur notre réponse.
     */
    @PostMapping
    public ResponseEntity<Void> signaler(@RequestBody @jakarta.validation.Valid Incident incident) {
        // WARN et non ERROR : c'est un symptôme rapporté par un poste client, pas une exception
        // du serveur. Les deux champs sont bornés par la validation ci-dessus, donc rien
        // d'arbitrairement long n'atteint les journaux.
        log.warn("Incident signalé par le navigateur [{}] : {}",
                incident.contexte(), incident.message());
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}

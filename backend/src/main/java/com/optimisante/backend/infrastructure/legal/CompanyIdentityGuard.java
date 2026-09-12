package com.optimisante.backend.infrastructure.legal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * Interdit de démarrer en production tant que l'identité légale n'est pas renseignée.
 *
 * <p>Un serveur qui refuse de partir se remarque dans la minute. Une facture au SIRET inventé,
 * elle, circule des mois avant que quiconque s'en aperçoive — et elle circule chez des CHU. Le
 * bruit est du bon côté.</p>
 *
 * <p>Hors production, le démarrage est autorisé mais l'avertissement est explicite : les
 * documents porteront « à compléter » à la place des mentions manquantes.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CompanyIdentityGuard {

    private final CompanyIdentity identite;
    private final Environment environment;

    @EventListener(ApplicationReadyEvent.class)
    public void verifier() {
        if (!identite.estIncomplete()) {
            log.info("Identité légale renseignée : {}", identite.denomination());
            return;
        }

        boolean production = Arrays.asList(environment.getActiveProfiles()).contains("prod");

        String message = """
                L'identité légale de l'entreprise est incomplète (app.legal.*).
                Renseignez au minimum : denomination, siret, adresse, tvaIntracom.
                Ces mentions s'impriment sur les devis, reçus et conventions remis aux clients \
                et aux CHU.""";

        if (production) {
            throw new IllegalStateException(message
                    + "\nDémarrage refusé en production : mieux vaut un serveur arrêté qu'un "
                    + "document portant une immatriculation inventée.");
        }
        log.warn("{}\nLes documents porteront « {} » à la place des mentions absentes.",
                message, CompanyIdentity.MANQUANT);
    }
}

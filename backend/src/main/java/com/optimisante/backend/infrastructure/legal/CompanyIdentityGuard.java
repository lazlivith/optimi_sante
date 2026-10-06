package com.optimisante.backend.infrastructure.legal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * Interdit de démarrer en production tant que l'identité légale n'est pas renseignée.
 *
 * <p>Un serveur qui refuse de partir se remarque dans la minute. Une facture au SIRET inventé,
 * elle, circule des mois avant que quiconque s'en aperçoive — et elle circule chez des CHU. Le
 * bruit est du bon côté.</p>
 *
 * <p>Hors production, le démarrage est autorisé mais l'avertissement est explicite : les
 * documents porteront « à compléter » à la place des mentions manquantes.</p>
 *
 * <p><b>Une mention « posée en attendant » compte comme absente.</b> Le contrôle ne cherchait
 * d'abord qu'une valeur vide — or personne ne laisse une variable vide : on y écrit « à
 * renseigner », le temps d'obtenir la vraie. Le 3 octobre 2026, les reçus de la plateforme
 * annonçaient ainsi « SIRET : IMMATRICULATION EN COURS » et « TVA : EN COURS D'ATTRIBUTION »
 * depuis des mois, et rien ne l'avait signalé : nous ne l'avons découvert qu'en lisant un
 * document de production. Voir {@link ValeurDAttente}.</p>
 *
 * <p>Le blocage porte sur les <b>quatre mentions obligatoires</b> et sur elles seules —
 * dénomination, SIRET, adresse, numéro de TVA. Les autres sont signalées en avertissement :
 * refuser un démarrage pour un numéro d'agrément absent arrêterait la vente d'équipements, qui
 * n'en dépend pas.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CompanyIdentityGuard {

    private final CompanyIdentity identite;
    private final Environment environment;

    @EventListener(ApplicationReadyEvent.class)
    public void verifier() {
        signalerLesMentionsSecondaires();

        List<String> aInstruire = identite.mentionsAInstruire();
        if (aInstruire.isEmpty()) {
            log.info("Identité légale renseignée : {}", identite.denomination());
            return;
        }

        boolean production = Arrays.asList(environment.getActiveProfiles()).contains("prod");

        // Nommer chaque variable et la valeur fautive : « l'identité est incomplète » envoie
        // chercher dans douze variables ; « LEGAL_SIRET contient IMMATRICULATION EN COURS » se
        // corrige en trente secondes.
        String message = """
                L'identité légale de l'entreprise n'est pas exploitable (app.legal.*) :
                %s
                Ces mentions s'impriment sur les devis, reçus et conventions remis aux clients \
                et aux CHU.""".formatted(String.join("\n", aInstruire));

        if (production) {
            throw new IllegalStateException(message
                    + "\nDémarrage refusé en production : mieux vaut un serveur arrêté qu'un "
                    + "document portant une immatriculation inventée — ou une mention d'attente, "
                    + "qui circule tout aussi bien et se remarque tout aussi peu.");
        }
        log.warn("{}\nLes documents porteront « {} » à la place des mentions absentes.",
                message, CompanyIdentity.MANQUANT);
    }

    /**
     * Les mentions qui ne bloquent pas, mais dont l'absence se voit sur un document.
     *
     * <p>Un numéro d'agrément manquant rend une convention de formation inopposable à un
     * financeur, et un IBAN d'attente fait virer dans le vide — mais aucun des deux n'empêche
     * de vendre un fauteuil roulant. L'avertissement est le bon niveau : visible au démarrage,
     * sans mettre la plateforme à l'arrêt pour une mention qui ne concerne qu'un métier.</p>
     */
    private void signalerLesMentionsSecondaires() {
        record Mention(String variable, String valeur) {}
        List<Mention> secondaires = List.of(
                new Mention("LEGAL_RCS", identite.rcs()),
                new Mention("LEGAL_CAPITAL_SOCIAL", identite.capitalSocial()),
                new Mention("LEGAL_IBAN", identite.iban()),
                new Mention("LEGAL_TELEPHONE", identite.telephone()),
                new Mention("LEGAL_NUMERO_AGREMENT", identite.numeroAgrement()));

        for (Mention mention : secondaires) {
            if (mention.valeur() == null || mention.valeur().isBlank()) {
                log.warn("Mention légale absente : {}. Les documents afficheront « {} ».",
                        mention.variable(), CompanyIdentity.MANQUANT);
            } else if (ValeurDAttente.enEst(mention.valeur())) {
                log.warn("Mention légale posée en attendant : {} = « {} ». "
                        + "Elle s'imprime telle quelle sur les documents.",
                        mention.variable(), mention.valeur());
            }
        }
    }
}

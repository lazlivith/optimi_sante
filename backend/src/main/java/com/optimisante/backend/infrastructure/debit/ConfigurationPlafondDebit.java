package com.optimisante.backend.infrastructure.debit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Assemble le plafond de débit.
 *
 * <p>Le filtre, lui, n'est <b>pas</b> déclaré comme bean : un bean de type {@code Filter} est
 * enregistré d'office par Spring Boot sur la totalité des requêtes, en plus de la place qu'on
 * lui donne dans la chaîne de sécurité. Il est donc construit dans {@code SecurityConfig}, qui
 * l'insère à l'endroit voulu — avant l'authentification, pour qu'une requête refusée ne coûte
 * ni décodage de jeton ni accès à la base.</p>
 */
@Slf4j
@Configuration
public class ConfigurationPlafondDebit {

    /**
     * L'horloge est un bean pour que les tests puissent avancer le temps sans attendre : un
     * plafond journalier ne se teste pas autrement qu'en déplaçant l'horloge d'un jour.
     */
    @Bean
    public Clock horlogePlafondDebit() {
        return Clock.systemUTC();
    }

    @Bean
    public PlafondDebit plafondDebit(ReglagesPlafondDebit reglages, Clock horlogePlafondDebit) {
        if (!reglages.actif()) {
            log.warn("Plafond de débit DÉSACTIVÉ par configuration : les routes d'IA ouvertes aux "
                    + "anonymes n'ont aucune limite. À n'utiliser que le temps de corriger un réglage.");
        } else {
            log.info("Plafond de débit actif : {}/min et {}/jour par adresse, {}/jour au total.",
                    reglages.plafondParAdresseParMinute(), reglages.plafondParAdresseParJour(),
                    reglages.plafondGlobalParJour());
        }
        return new PlafondDebit(reglages, horlogePlafondDebit);
    }
}

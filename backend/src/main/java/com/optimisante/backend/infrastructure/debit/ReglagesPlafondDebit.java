package com.optimisante.backend.infrastructure.debit;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Les trois plafonds, réglables sans redéploiement.
 *
 * <p><b>Les valeurs par défaut sont volontairement généreuses pour un humain et serrées pour
 * un script.</b> Un visiteur qui pose des questions à l'assistant en envoie une toutes les
 * quinze ou vingt secondes au plus vite : vingt par minute lui laisse trois fois cette marge,
 * et deux cents par jour couvrent une session de travail entière. Un script, lui, bute dès la
 * première seconde.</p>
 *
 * <p><b>Le plafond global est un budget, pas une limite technique.</b> C'est le seul des trois
 * réglages dont la bonne valeur ne se déduit pas du comportement des visiteurs mais de ce qu'on
 * accepte de dépenser. Arbitré à <b>500 appels par jour</b> le 14 novembre 2026 : le trafic est
 * faible aujourd'hui, et un plafond posé au-dessus du trafic réel ne borne rien. Il est à relever
 * le jour où l'assistant sera réellement utilisé — et le journal le dira, puisque la coupure
 * globale est tracée en {@code ERROR} avec la mention « si ce n'est pas un abus, le plafond est
 * trop bas ».</p>
 *
 * @param actif                      {@code false} désactive entièrement le filtre, pour qu'un
 *                                   plafond mal réglé ne devienne jamais une panne qu'il faut
 *                                   redéployer pour corriger
 * @param plafondParAdresseParMinute requêtes admises par adresse et par minute
 * @param plafondParAdresseParJour   requêtes admises par adresse et par jour
 * @param plafondGlobalParJour       requêtes admises toutes adresses confondues et par jour ;
 *                                   au delà, l'assistant est coupé jusqu'au lendemain
 */
@ConfigurationProperties(prefix = "app.plafond-debit")
public record ReglagesPlafondDebit(
        Boolean actif,
        Integer plafondParAdresseParMinute,
        Integer plafondParAdresseParJour,
        Integer plafondGlobalParJour) {

    public ReglagesPlafondDebit {
        actif = actif == null || actif;
        plafondParAdresseParMinute = plafondParAdresseParMinute == null ? 20 : plafondParAdresseParMinute;
        plafondParAdresseParJour = plafondParAdresseParJour == null ? 200 : plafondParAdresseParJour;
        plafondGlobalParJour = plafondGlobalParJour == null ? 500 : plafondGlobalParJour;
    }

    /** Réglages par défaut, utilisés quand la configuration ne dit rien. */
    public static ReglagesPlafondDebit defaut() {
        return new ReglagesPlafondDebit(null, null, null, null);
    }
}

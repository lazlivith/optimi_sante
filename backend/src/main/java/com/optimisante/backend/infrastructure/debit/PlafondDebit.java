package com.optimisante.backend.infrastructure.debit;

import lombok.extern.slf4j.Slf4j;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Les trois plafonds qui protègent l'assistant d'IA — et surtout la facture qu'il engendre.
 *
 * <p>L'assistant répond à des visiteurs anonymes : avant ce garde-fou, une seule personne
 * pouvait, en une nuit, produire autant d'appels payants qu'elle le souhaitait. Les trois
 * plafonds répondent à trois scénarios différents, et c'est pourquoi il en faut trois :</p>
 *
 * <ol>
 *   <li><b>Par adresse et par minute</b> — le bourrage : quelqu'un qui tient la touche Entrée.</li>
 *   <li><b>Par adresse et par jour</b> — l'abus patient : un script qui envoie une question
 *       toutes les dix secondes toute la journée passerait le plafond par minute sans difficulté.</li>
 *   <li><b>Global et par jour</b> — le dernier rempart : cent adresses différentes, chacune sous
 *       son plafond, dépassent ensemble n'importe quel budget. C'est le seul des trois qui borne
 *       réellement la dépense, et c'est le seul qui coupe l'assistant pour tout le monde.</li>
 * </ol>
 *
 * <p><b>Limite assumée, et sa condition de sortie.</b> Ces compteurs vivent en mémoire. Ils sont
 * exacts tant que le backend tourne sur <b>une</b> instance, ce qui est le cas sur Render
 * aujourd'hui. Le jour où il en tournera deux, chacune aura ses propres compteurs et les
 * plafonds réels vaudront le double de ceux annoncés. Ce jour-là — le même que celui de
 * {@code SEC‑6e}, le verrou partagé des tâches planifiées — il faudra un état partagé, et c'est
 * le moment où une bibliothèque comme bucket4j adossée à Redis devient la bonne réponse. Pas
 * avant : ce qu'elle apporterait aujourd'hui, nous ne l'utiliserions pas.</p>
 *
 * <p><b>La carte des adresses est bornée.</b> Un compteur par adresse est une fuite de mémoire
 * déguisée en sécurité : il suffit de faire varier l'adresse source pour la faire grossir. Au
 * delà de {@link #ADRESSES_MAXIMUM} entrées, les adresses inactives sont oubliées ; si cela ne
 * suffit pas, la carte est vidée et l'événement journalisé — perdre des compteurs par adresse
 * est sans gravité, le plafond global, lui, tient toujours.</p>
 */
@Slf4j
public class PlafondDebit {

    /** Au delà, on fait le ménage. Une entrée pèse quelques dizaines d'octets. */
    static final int ADRESSES_MAXIMUM = 50_000;

    private final Clock horloge;
    private final ReglagesPlafondDebit reglages;

    /** Un seau par adresse, pour la fenêtre d'une minute. */
    private final Map<String, SeauDeJetons> parMinute = new ConcurrentHashMap<>();

    /** Un seau par adresse, pour la fenêtre d'un jour. */
    private final Map<String, SeauDeJetons> parJour = new ConcurrentHashMap<>();

    /** Le compteur global : un seul seau, partagé par tous les visiteurs. */
    private final SeauDeJetons global;

    /** Pour ne journaliser la coupure globale qu'une fois par jour et non à chaque requête. */
    private final AtomicLong derniereAlerteGlobale = new AtomicLong(0);

    /** Ce que ce plafond protège, pour que le journal nomme la bonne chose quand il coupe. */
    private final String objet;

    public PlafondDebit(ReglagesPlafondDebit reglages, Clock horloge) {
        this(reglages, horloge, "L'assistant");
    }

    /**
     * @param objet sujet de la phrase journalisée à la coupure globale, par exemple
     *              « Le dépôt de partenariat » : un second plafond qui annoncerait la coupure
     *              de « l'assistant » enverrait l'exploitant chercher au mauvais endroit
     */
    public PlafondDebit(ReglagesPlafondDebit reglages, Clock horloge, String objet) {
        this.objet = objet;
        this.reglages = reglages;
        this.horloge = horloge;
        this.global = new SeauDeJetons(reglages.plafondGlobalParJour(), Duration.ofDays(1), horloge.instant());
    }

    /**
     * Vérifie les trois plafonds pour une adresse, et ne consomme un jeton que si les trois
     * passent.
     *
     * <p><b>Aucun refus ne débite quoi que ce soit, et ce n'était pas le cas dans la première
     * version de ce fichier.</b> Elle consommait chaque seau au fur et à mesure, ce qui
     * produisait deux défauts opposés :</p>
     *
     * <ul>
     *   <li>un refus du plafond global débitait le crédit du visiteur, qui perdait des appels
     *       qu'il n'a jamais obtenus ;</li>
     *   <li>un refus du plafond par adresse débitait le crédit <b>global</b> — bien plus grave :
     *       un seul abuseur pouvait épuiser le budget du jour pour tout le monde
     *       <b>sans jamais déclencher un appel payant</b>, et donc sans qu'il en coûte un
     *       centime, ce qui est exactement l'inverse du but.</li>
     * </ul>
     *
     * <p>D'où le déroulé en deux temps : on <b>regarde</b> d'abord les deux plafonds de
     * l'adresse, on consomme ensuite le global, puis l'adresse.</p>
     *
     * <p><b>La petite inexactitude que cela laisse, et pourquoi elle est acceptable.</b> Entre
     * le regard et la consommation, une requête concurrente de la <b>même</b> adresse peut
     * prendre le dernier jeton : deux requêtes passent alors là où une seule aurait dû. Le
     * dépassement est au plus d'un appel par adresse et par fenêtre. Le plafond global, lui,
     * est consommé d'un seul geste atomique et reste <b>exact</b> — c'est celui qui borne la
     * dépense, et c'est pour cela qu'il est le seul à ne tolérer aucune approximation.</p>
     */
    public VerdictPlafond verifier(String adresse) {
        Instant maintenant = horloge.instant();

        SeauDeJetons seauMinute = seau(parMinute, adresse, reglages.plafondParAdresseParMinute(),
                Duration.ofMinutes(1), maintenant);
        if (seauMinute.restants(maintenant) <= 0) {
            return VerdictPlafond.refus(TypePlafond.ADRESSE_PAR_MINUTE,
                    seauMinute.secondesAvantReouverture(maintenant));
        }

        SeauDeJetons seauJour = seau(parJour, adresse, reglages.plafondParAdresseParJour(),
                Duration.ofDays(1), maintenant);
        if (seauJour.restants(maintenant) <= 0) {
            return VerdictPlafond.refus(TypePlafond.ADRESSE_PAR_JOUR,
                    seauJour.secondesAvantReouverture(maintenant));
        }

        if (!global.consommer(maintenant)) {
            journaliserCoupureGlobale(maintenant);
            return VerdictPlafond.refus(TypePlafond.GLOBAL_JOURNALIER,
                    global.secondesAvantReouverture(maintenant));
        }

        seauMinute.consommer(maintenant);
        seauJour.consommer(maintenant);
        return VerdictPlafond.autorise();
    }

    /** Jetons globaux restants aujourd'hui. Pour la supervision et les tests. */
    public long creditGlobalRestant() {
        return global.restants(horloge.instant());
    }

    /** Nombre d'adresses suivies. Pour les tests de la borne mémoire. */
    int adressesSuivies() {
        return parMinute.size();
    }

    /**
     * Crédit restant à une adresse pour la minute courante, {@code -1} si elle n'est pas suivie.
     *
     * <p>Exposé pour que les tests puissent vérifier qu'un <b>refus ne débite rien</b> — la
     * propriété que la première version de {@link #verifier(String)} violait dans les deux
     * sens. Sans cet accès, elle ne se vérifie que par des détours dont on ne sait plus ce
     * qu'ils testent.</p>
     */
    long creditAdresseParMinute(String adresse) {
        SeauDeJetons seau = parMinute.get(adresse);
        return seau == null ? -1L : seau.restants(horloge.instant());
    }

    private SeauDeJetons seau(Map<String, SeauDeJetons> carte, String adresse, long capacite,
                              Duration periode, Instant maintenant) {
        if (carte.size() >= ADRESSES_MAXIMUM) {
            oublierLesInactives(carte, periode, maintenant);
        }
        return carte.computeIfAbsent(adresse, ignore -> new SeauDeJetons(capacite, periode, maintenant));
    }

    /**
     * Retire les adresses dont la fenêtre est écoulée : leur seau est de toute façon équivalent
     * à un seau neuf. Si la carte reste pleine — ce qui signifie que des dizaines de milliers
     * d'adresses sont actives simultanément, donc une attaque distribuée — on la vide.
     */
    private void oublierLesInactives(Map<String, SeauDeJetons> carte, Duration periode, Instant maintenant) {
        long limite = maintenant.toEpochMilli() - periode.toMillis();
        Iterator<Map.Entry<String, SeauDeJetons>> it = carte.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue().debutFenetreEnMillis() < limite) {
                it.remove();
            }
        }
        if (carte.size() >= ADRESSES_MAXIMUM) {
            log.warn("Plafond de débit : {} adresses actives simultanément, compteurs par adresse "
                    + "remis à zéro. Le plafond global reste en vigueur.", carte.size());
            carte.clear();
        }
    }

    private void journaliserCoupureGlobale(Instant maintenant) {
        long jour = maintenant.toEpochMilli() / Duration.ofDays(1).toMillis();
        if (derniereAlerteGlobale.getAndSet(jour) != jour) {
            log.error("Plafond de débit GLOBAL atteint : {} appels pour la journée. {} "
                            + "est coupé jusqu'à demain. Si ce n'est pas un abus, le plafond est trop bas.",
                    reglages.plafondGlobalParJour(), objet);
        }
    }

    /** Lequel des trois plafonds a refusé, pour que le message au visiteur soit juste. */
    public enum TypePlafond {
        ADRESSE_PAR_MINUTE,
        ADRESSE_PAR_JOUR,
        GLOBAL_JOURNALIER
    }

    /**
     * Le résultat d'une vérification. {@code secondesAvantReouverture} alimente l'en-tête
     * {@code Retry-After}, pour qu'un client correct sache quand revenir au lieu de réessayer
     * en boucle — ce qui, sans cette indication, transforme un plafond en tempête de requêtes.
     */
    public record VerdictPlafond(boolean permis, TypePlafond plafondAtteint, long secondesAvantReouverture) {

        static VerdictPlafond autorise() {
            return new VerdictPlafond(true, null, 0L);
        }

        static VerdictPlafond refus(TypePlafond type, long secondes) {
            return new VerdictPlafond(false, type, secondes);
        }
    }
}

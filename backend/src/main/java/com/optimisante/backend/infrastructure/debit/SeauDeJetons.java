package com.optimisante.backend.infrastructure.debit;

import java.time.Duration;
import java.time.Instant;

/**
 * Un seau de jetons : {@code capacite} jetons, rechargés intégralement à chaque période.
 *
 * <p><b>Rechargement par fenêtre, et non continu.</b> Un seau classique rend un jeton toutes
 * les {@code periode / capacite}. Ici le seau se remplit d'un coup au changement de fenêtre.
 * C'est volontaire et c'est ce que le visiteur comprend : « vingt messages par minute » veut
 * dire que la minute suivante en rend vingt, pas qu'un vingtième de minute en rend un. Le prix
 * de ce choix est qu'un visiteur peut consommer deux capacités à cheval sur deux fenêtres —
 * quarante messages en deux secondes au pire. Pour un plafond dont le but est de borner une
 * facture, ce doublement est sans conséquence ; pour un plafond anti-bourrage, il faudrait le
 * rechargement continu.</p>
 *
 * <p><b>Mémoire seulement.</b> Ce compteur vit dans la machine virtuelle. Le jour où le backend
 * tournera sur deux instances, chacune aura son propre seau et le plafond réel sera donc le
 * double de celui annoncé — voir l'avertissement de {@link PlafondDebit}.</p>
 */
public final class SeauDeJetons {

    private final long capacite;
    private final long periodeEnMillis;

    /** Début de la fenêtre courante, en millisecondes depuis l'époque. */
    private long debutFenetre;

    /** Jetons restants dans la fenêtre courante. */
    private long restants;

    public SeauDeJetons(long capacite, Duration periode, Instant maintenant) {
        if (capacite <= 0) {
            throw new IllegalArgumentException("La capacité d'un seau est strictement positive : " + capacite);
        }
        if (periode == null || periode.isZero() || periode.isNegative()) {
            throw new IllegalArgumentException("La période d'un seau est strictement positive : " + periode);
        }
        this.capacite = capacite;
        this.periodeEnMillis = periode.toMillis();
        this.debutFenetre = maintenant.toEpochMilli();
        this.restants = capacite;
    }

    /**
     * Consomme un jeton si la fenêtre courante en a encore.
     *
     * <p>Synchronisé : deux requêtes concurrentes sur la même adresse passent par ici, et un
     * {@code restants--} non protégé laisserait passer plus d'appels que la capacité.</p>
     *
     * @return {@code true} si le jeton a été consommé, {@code false} si le plafond est atteint
     */
    public synchronized boolean consommer(Instant maintenant) {
        rafraichir(maintenant);
        if (restants <= 0) {
            return false;
        }
        restants--;
        return true;
    }

    /** Secondes restant avant que la fenêtre ne se rouvre. Au moins 1, pour un {@code Retry-After} utile. */
    public synchronized long secondesAvantReouverture(Instant maintenant) {
        rafraichir(maintenant);
        long ecoule = maintenant.toEpochMilli() - debutFenetre;
        long restantEnMillis = periodeEnMillis - ecoule;
        return Math.max(1L, (restantEnMillis + 999L) / 1000L);
    }

    /** Jetons encore disponibles dans la fenêtre courante. Exposé pour les tests et la supervision. */
    public synchronized long restants(Instant maintenant) {
        rafraichir(maintenant);
        return restants;
    }

    /**
     * Instant de la dernière activité observée, pour que {@link PlafondDebit} puisse oublier
     * les adresses inactives sans garder une carte qui grossit indéfiniment.
     */
    public synchronized long debutFenetreEnMillis() {
        return debutFenetre;
    }

    /**
     * Ouvre une nouvelle fenêtre si la précédente est écoulée.
     *
     * <p>Le calcul repose sur l'horloge passée en argument et jamais sur
     * {@code Instant.now()} : c'est ce qui rend le franchissement d'une fenêtre d'un jour
     * testable sans attendre un jour.</p>
     */
    private void rafraichir(Instant maintenant) {
        long millis = maintenant.toEpochMilli();
        if (millis - debutFenetre >= periodeEnMillis) {
            // Aligner sur la fenêtre courante plutôt que sur « maintenant » : sans cela, une
            // requête isolée toutes les deux périodes décalerait indéfiniment l'origine.
            long fenetresEcoulees = (millis - debutFenetre) / periodeEnMillis;
            debutFenetre += fenetresEcoulees * periodeEnMillis;
            restants = capacite;
        }
    }
}

package com.optimisante.backend.infrastructure.debit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Les trois plafonds, et surtout les cas où ils ne doivent <b>pas</b> se déclencher.
 *
 * <p>Chaque test avance une horloge contrôlée : un plafond journalier ne se vérifie pas
 * autrement qu'en déplaçant le temps d'un jour, et un test qui attendrait réellement serait un
 * test instable.</p>
 */
@DisplayName("Plafonds de débit des routes d'IA")
class PlafondDebitTest {

    /** Horloge qu'un test fait avancer à la main. */
    private static final class HorlogeReglable extends Clock {
        private Instant instant = Instant.parse("2026-11-12T10:00:00Z");

        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }

        void avancerDe(Duration duree) { instant = instant.plus(duree); }
    }

    private static ReglagesPlafondDebit reglages(int parMinute, int parJour, int global) {
        return new ReglagesPlafondDebit(true, parMinute, parJour, global);
    }

    @Test
    @DisplayName("sous le plafond, tout passe")
    void sousLePlafondToutPasse() {
        HorlogeReglable horloge = new HorlogeReglable();
        PlafondDebit plafond = new PlafondDebit(reglages(5, 100, 1000), horloge);

        for (int i = 0; i < 5; i++) {
            assertThat(plafond.verifier("10.0.0.1").permis())
                    .as("requête numéro %d", i + 1)
                    .isTrue();
        }
    }

    @Test
    @DisplayName("la sixième requête de la minute est refusée, et dit laquelle des limites")
    void sixiemeRequeteRefusee() {
        HorlogeReglable horloge = new HorlogeReglable();
        PlafondDebit plafond = new PlafondDebit(reglages(5, 100, 1000), horloge);

        for (int i = 0; i < 5; i++) {
            plafond.verifier("10.0.0.1");
        }

        PlafondDebit.VerdictPlafond verdict = plafond.verifier("10.0.0.1");
        assertThat(verdict.permis()).isFalse();
        assertThat(verdict.plafondAtteint()).isEqualTo(PlafondDebit.TypePlafond.ADRESSE_PAR_MINUTE);
        assertThat(verdict.secondesAvantReouverture())
                .as("un Retry-After nul ferait réessayer immédiatement")
                .isGreaterThan(0);
    }

    @Test
    @DisplayName("la minute suivante rend le crédit")
    void laMinuteSuivanteRendLeCredit() {
        HorlogeReglable horloge = new HorlogeReglable();
        PlafondDebit plafond = new PlafondDebit(reglages(5, 100, 1000), horloge);

        for (int i = 0; i < 6; i++) {
            plafond.verifier("10.0.0.1");
        }
        assertThat(plafond.verifier("10.0.0.1").permis()).isFalse();

        horloge.avancerDe(Duration.ofSeconds(61));

        assertThat(plafond.verifier("10.0.0.1").permis()).isTrue();
    }

    @Test
    @DisplayName("une adresse plafonnée n'empêche pas une autre adresse de passer")
    void uneAdressePlafonneeNEmpecheRienAuxAutres() {
        HorlogeReglable horloge = new HorlogeReglable();
        PlafondDebit plafond = new PlafondDebit(reglages(2, 100, 1000), horloge);

        plafond.verifier("10.0.0.1");
        plafond.verifier("10.0.0.1");
        assertThat(plafond.verifier("10.0.0.1").permis()).isFalse();

        assertThat(plafond.verifier("10.0.0.2").permis())
                .as("le plafond est par adresse, pas par service")
                .isTrue();
    }

    @Test
    @DisplayName("le plafond journalier par adresse tient même si chaque minute est respectée")
    void plafondJournalierParAdresse() {
        HorlogeReglable horloge = new HorlogeReglable();
        // Dix par minute, mais seulement quinze par jour : c'est le scénario du script patient.
        PlafondDebit plafond = new PlafondDebit(reglages(10, 15, 1000), horloge);

        int acceptees = 0;
        for (int minute = 0; minute < 10; minute++) {
            for (int i = 0; i < 5; i++) {
                if (plafond.verifier("10.0.0.1").permis()) {
                    acceptees++;
                }
            }
            horloge.avancerDe(Duration.ofSeconds(61));
        }

        assertThat(acceptees)
                .as("quinze par jour, atteintes en trois minutes, puis plus rien")
                .isEqualTo(15);
    }

    @Test
    @DisplayName("le plafond global coupe l'assistant même si chaque adresse est sous sa limite")
    void plafondGlobalCoupeToutLeMonde() {
        HorlogeReglable horloge = new HorlogeReglable();
        // Chaque adresse a droit à beaucoup ; le total, non. C'est le scénario distribué.
        PlafondDebit plafond = new PlafondDebit(reglages(100, 100, 10), horloge);

        for (int i = 0; i < 10; i++) {
            assertThat(plafond.verifier("10.0.0." + i).permis()).isTrue();
        }

        PlafondDebit.VerdictPlafond verdict = plafond.verifier("10.0.0.200");
        assertThat(verdict.permis())
                .as("une adresse jamais vue est refusée elle aussi : c'est le but du plafond global")
                .isFalse();
        assertThat(verdict.plafondAtteint()).isEqualTo(PlafondDebit.TypePlafond.GLOBAL_JOURNALIER);
    }

    @Test
    @DisplayName("un refus global ne débite pas le crédit de l'adresse")
    void leRefusGlobalNeDebitePasLAdresse() {
        HorlogeReglable horloge = new HorlogeReglable();
        PlafondDebit plafond = new PlafondDebit(reglages(3, 100, 1), horloge);

        assertThat(plafond.verifier("10.0.0.1").permis()).isTrue();
        assertThat(plafond.creditAdresseParMinute("10.0.0.1")).isEqualTo(2);

        // Le global est épuisé : les deux tentatives suivantes sont refusées par lui.
        assertThat(plafond.verifier("10.0.0.1").plafondAtteint())
                .isEqualTo(PlafondDebit.TypePlafond.GLOBAL_JOURNALIER);
        assertThat(plafond.verifier("10.0.0.1").plafondAtteint())
                .isEqualTo(PlafondDebit.TypePlafond.GLOBAL_JOURNALIER);

        assertThat(plafond.creditAdresseParMinute("10.0.0.1"))
                .as("le visiteur serait débité de deux appels qu'il n'a jamais obtenus")
                .isEqualTo(2);
    }

    @Test
    @DisplayName("un refus par adresse ne débite pas le budget global — le défaut le plus grave")
    void leRefusParAdresseNeDebitePasLeGlobal() {
        HorlogeReglable horloge = new HorlogeReglable();
        PlafondDebit plafond = new PlafondDebit(reglages(1, 100, 5), horloge);

        assertThat(plafond.verifier("10.0.0.1").permis()).isTrue();
        assertThat(plafond.creditGlobalRestant()).isEqualTo(4);

        // Cinquante tentatives au delà du plafond par minute. Dans la première version de
        // PlafondDebit, chacune débitait le global : un seul abuseur épuisait le budget du jour
        // pour tout le monde SANS déclencher un seul appel payant.
        for (int i = 0; i < 50; i++) {
            assertThat(plafond.verifier("10.0.0.1").plafondAtteint())
                    .isEqualTo(PlafondDebit.TypePlafond.ADRESSE_PAR_MINUTE);
        }

        assertThat(plafond.creditGlobalRestant())
                .as("un refus ne coûte rien, donc ne doit rien consommer du budget")
                .isEqualTo(4);

        // Et le budget est toujours là pour les autres visiteurs.
        assertThat(plafond.verifier("10.0.0.2").permis()).isTrue();
    }

    @Test
    @DisplayName("le lendemain, le crédit global est rendu")
    void leLendemainLeGlobalEstRendu() {
        HorlogeReglable horloge = new HorlogeReglable();
        PlafondDebit plafond = new PlafondDebit(reglages(100, 100, 2), horloge);

        plafond.verifier("10.0.0.1");
        plafond.verifier("10.0.0.1");
        assertThat(plafond.verifier("10.0.0.1").permis()).isFalse();

        horloge.avancerDe(Duration.ofDays(1).plusSeconds(1));

        assertThat(plafond.verifier("10.0.0.1").permis()).isTrue();
        assertThat(plafond.creditGlobalRestant()).isEqualTo(1);
    }

    @Test
    @DisplayName("la carte des adresses reste bornée : un compteur par adresse n'est pas une fuite")
    void laCarteDesAdressesResteBornee() {
        HorlogeReglable horloge = new HorlogeReglable();
        PlafondDebit plafond = new PlafondDebit(
                reglages(100, 100, Integer.MAX_VALUE), horloge);

        // Plus d'adresses distinctes que la borne, comme le ferait une attaque qui fait varier
        // l'en-tête X-Forwarded-For à chaque requête.
        for (int i = 0; i < PlafondDebit.ADRESSES_MAXIMUM + 5_000; i++) {
            plafond.verifier("10." + (i / 65536) + "." + ((i / 256) % 256) + "." + (i % 256));
        }

        assertThat(plafond.adressesSuivies())
                .as("la carte ne doit pas grossir indéfiniment")
                .isLessThanOrEqualTo(PlafondDebit.ADRESSES_MAXIMUM);
    }

    @Test
    @DisplayName("les réglages absents prennent des valeurs par défaut sûres")
    void reglagesParDefaut() {
        ReglagesPlafondDebit defaut = ReglagesPlafondDebit.defaut();

        assertThat(defaut.actif()).as("actif par défaut : on ne désactive pas un garde-fou par omission").isTrue();
        assertThat(defaut.plafondParAdresseParMinute()).isEqualTo(20);
        assertThat(defaut.plafondParAdresseParJour()).isEqualTo(200);
        assertThat(defaut.plafondGlobalParJour()).isEqualTo(500);
    }
}

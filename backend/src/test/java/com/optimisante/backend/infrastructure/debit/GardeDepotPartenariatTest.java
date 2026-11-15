package com.optimisante.backend.infrastructure.debit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GardeDepotPartenariatTest {

    /** Une horloge qu'on avance à la main : un délai ne se teste pas en attendant. */
    private static final class HorlogeReglable extends Clock {
        private Instant maintenant = Instant.parse("2026-11-15T10:00:00Z");

        void avancer(Duration duree) {
            maintenant = maintenant.plus(duree);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return maintenant;
        }
    }

    private final HorlogeReglable horloge = new HorlogeReglable();
    private final GardeDepotPartenariat garde =
            new GardeDepotPartenariat(ReglagesDepotPartenariat.defaut(), horloge);

    @Test
    @DisplayName("un formulaire rempli en une minute, champ piège vide, passe")
    void envoiHumainAccepte() {
        String jeton = garde.emettreJeton();
        horloge.avancer(Duration.ofMinutes(1));

        assertThatCode(() -> garde.verifierEnvoi(jeton, null)).doesNotThrowAnyException();
        assertThatCode(() -> garde.verifierEnvoi(jeton, "  ")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("un envoi dans la seconde qui suit l'affichage est refusé, puis accepté passé le délai")
    void envoiTropRapideRefuse() {
        String jeton = garde.emettreJeton();
        horloge.avancer(Duration.ofSeconds(1));

        assertThatThrownBy(() -> garde.verifierEnvoi(jeton, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("trop vite");

        horloge.avancer(Duration.ofSeconds(5));
        assertThatCode(() -> garde.verifierEnvoi(jeton, null)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("un formulaire resté ouvert au-delà de sa validité est refusé avec la marche à suivre")
    void formulairePerime() {
        String jeton = garde.emettreJeton();
        horloge.avancer(Duration.ofMinutes(241));

        assertThatThrownBy(() -> garde.verifierEnvoi(jeton, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Rechargez la page");
    }

    @Test
    @DisplayName("un champ piège rempli est refusé, même avec un jeton valable")
    void champPiegeRempli() {
        String jeton = garde.emettreJeton();
        horloge.avancer(Duration.ofMinutes(1));

        assertThatThrownBy(() -> garde.verifierEnvoi(jeton, "https://exemple.invalid"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("n'a pas pu être vérifiée");
    }

    @Test
    @DisplayName("sans jeton, avec un jeton inventé ou antidaté, l'envoi est refusé")
    void jetonAbsentOuFalsifie() {
        String vrai = garde.emettreJeton();
        horloge.avancer(Duration.ofMinutes(1));
        String signature = vrai.substring(vrai.indexOf('.') + 1);
        long emisLe = Long.parseLong(vrai.substring(0, vrai.indexOf('.')));

        for (String faux : new String[]{
                null, "", "abc", "123", ".", "123.",
                // L'instant reculé d'une heure, la signature d'origine : c'est la falsification
                // qu'un script tenterait pour franchir le délai minimum.
                (emisLe - 3_600_000) + "." + signature,
                emisLe + "." + signature.substring(1) + "A",
                "x".repeat(5_000)}) {
            assertThatThrownBy(() -> garde.verifierEnvoi(faux, null))
                    .as("jeton : %s", faux == null ? "null" : faux.substring(0, Math.min(40, faux.length())))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    @DisplayName("un jeton émis avant un redémarrage n'est plus reconnu")
    void cleProprePAuProcessus() {
        String jeton = garde.emettreJeton();
        horloge.avancer(Duration.ofMinutes(1));

        GardeDepotPartenariat apresRedemarrage =
                new GardeDepotPartenariat(ReglagesDepotPartenariat.defaut(), horloge);
        assertThatThrownBy(() -> apresRedemarrage.verifierEnvoi(jeton, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Rechargez la page");
    }

    @Test
    @DisplayName("garde désactivée : ni jeton ni champ piège ne sont exigés")
    void gardeDesactivee() {
        GardeDepotPartenariat inactive = new GardeDepotPartenariat(
                new ReglagesDepotPartenariat(false, null, null, null, null, null, null), horloge);

        assertThat(inactive.actif()).isFalse();
        assertThatCode(() -> inactive.verifierEnvoi(null, "rempli")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("le plafond de débit du dépôt a ses propres compteurs et ses propres valeurs")
    void plafondPropre() {
        for (int i = 0; i < 10; i++) {
            assertThat(garde.verifierDebit("203.0.113.7").permis()).as("envoi %d", i + 1).isTrue();
        }
        PlafondDebit.VerdictPlafond onzieme = garde.verifierDebit("203.0.113.7");
        assertThat(onzieme.permis()).isFalse();
        assertThat(onzieme.plafondAtteint()).isEqualTo(PlafondDebit.TypePlafond.ADRESSE_PAR_MINUTE);
        assertThat(garde.verifierDebit("203.0.113.8").permis()).isTrue();
    }
}

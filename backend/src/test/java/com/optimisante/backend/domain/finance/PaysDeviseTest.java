package com.optimisante.backend.domain.finance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La devise suggérée selon le pays.
 *
 * <p>Ce qui est éprouvé ici n'est pas la liste pour elle-même, mais la distinction que l'on
 * perd le plus facilement : les deux francs CFA ne sont <b>pas</b> la même monnaie. Dakar est
 * en XOF, Brazzaville en XAF. Confondre les deux ferait facturer un client sénégalais dans une
 * devise que sa banque ne connaît pas — et le code ISO diffère, donc Stripe refuserait.</p>
 */
class PaysDeviseTest {

    @Test
    @DisplayName("l'Afrique de l'Ouest est en franc CFA BCEAO")
    void afriqueOuest() {
        for (String pays : new String[]{"SN", "CI", "BJ", "BF", "ML", "NE", "TG", "GW"}) {
            assertThat(PaysDevise.suggestion(pays))
                    .as("pays %s", pays)
                    .contains(Devise.de("XOF"));
        }
    }

    @Test
    @DisplayName("l'Afrique centrale est en franc CFA BEAC")
    void afriqueCentrale() {
        for (String pays : new String[]{"CG", "CM", "GA", "TD", "CF", "GQ"}) {
            assertThat(PaysDevise.suggestion(pays))
                    .as("pays %s", pays)
                    .contains(Devise.de("XAF"));
        }
    }

    @Test
    @DisplayName("les deux francs CFA ne se confondent pas")
    void deuxFrancsDistincts() {
        assertThat(PaysDevise.suggestion("SN")).isNotEqualTo(PaysDevise.suggestion("CG"));
    }

    @Test
    @DisplayName("un pays sans zone monétaire servie ne reçoit aucune suggestion")
    void aucuneSuggestion() {
        // Vide, et non « euro par defaut » : l'appelant doit pouvoir distinguer un pays dont on
        // ne sait rien d'un pays de la zone euro.
        for (String pays : new String[]{"FR", "US", "MA", null, "", "   "}) {
            assertThat(PaysDevise.suggestion(pays)).as("pays %s", pays).isEmpty();
        }
    }

    @Test
    @DisplayName("la casse et les espaces sont tolérés")
    void casseEtEspaces() {
        assertThat(PaysDevise.suggestion(" sn ")).contains(Devise.de("XOF"));
        assertThat(PaysDevise.suggestion("ci")).contains(Devise.de("XOF"));
    }
}

package com.optimisante.backend.domain.finance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * L'arrondi au palier, cœur de la conversion d'un prix.
 *
 * <p>Deux propriétés s'y jouent, et perdre l'une ou l'autre coûte de l'argent ou de la
 * lisibilité : le montant converti ne descend <b>jamais</b> sous le prix de référence, et il
 * tombe sur un multiple du palier — un prix affiché 327 978,5 FCFA ne se lit pas, et n'existe
 * d'ailleurs pas, le franc CFA n'ayant pas de subdivision.</p>
 */
class ArrondiPalierTest {

    private static BigDecimal palier(String valeur, String palier) {
        return ServiceDevises.auPalierSuperieur(new BigDecimal(valeur), new BigDecimal(palier));
    }

    @Test
    @DisplayName("un prix converti remonte au millier supérieur")
    void milleFrancs() {
        // 500 EUR x 655,957 = 327 978,50 -> 328 000 FCFA.
        assertThat(palier("327978.50", "1000")).isEqualByComparingTo("328000");
        assertThat(palier("1", "1000")).isEqualByComparingTo("1000");
    }

    @Test
    @DisplayName("un montant déjà au palier ne bouge pas")
    void dejaAuPalier() {
        assertThat(palier("328000", "1000")).isEqualByComparingTo("328000");
        assertThat(palier("0", "1000")).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("l'arrondi remonte toujours, jamais vers le bas")
    void jamaisVersLeBas() {
        // Un arrondi au plus proche aurait rendu 327 000 : vendre 4,54 EUR de moins a chaque
        // commande, sans que personne ne l'ait decide.
        assertThat(palier("327001", "1000")).isEqualByComparingTo("328000");
        assertThat(palier("327999.99", "1000")).isEqualByComparingTo("328000");
    }

    @Test
    @DisplayName("le palier au centime laisse un prix en euros intact")
    void palierCentime() {
        assertThat(palier("49.90", "0.01")).isEqualByComparingTo("49.90");
        assertThat(palier("19.99", "0.01")).isEqualByComparingTo("19.99");
    }

    @Test
    @DisplayName("un palier de cinq francs convient aux petits montants")
    void palierFin() {
        assertThat(palier("3279.785", "5")).isEqualByComparingTo("3280");
        assertThat(palier("3281", "5")).isEqualByComparingTo("3285");
    }
}

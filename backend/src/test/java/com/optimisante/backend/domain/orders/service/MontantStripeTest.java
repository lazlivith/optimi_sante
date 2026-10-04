package com.optimisante.backend.domain.orders.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * La conversion d'un montant vers la plus petite unité monétaire.
 *
 * <p>Ce qui est éprouvé ici n'est pas l'arithmétique — elle tient en une ligne — mais la seule
 * chose qui puisse coûter de l'argent : qu'une devise sans subdivision ne soit pas multipliée
 * par cent, et qu'une devise dont la règle n'est pas implémentée soit refusée au lieu d'être
 * approximée.</p>
 */
class MontantStripeTest {

    @Test
    @DisplayName("l'euro se convertit en centimes")
    void euro() {
        assertThat(MontantStripe.versPlusPetiteUnite(new BigDecimal("19.99"), "eur")).isEqualTo(1999);
        assertThat(MontantStripe.versPlusPetiteUnite(new BigDecimal("500.00"), "eur")).isEqualTo(50_000);
        assertThat(MontantStripe.versPlusPetiteUnite(BigDecimal.ZERO, "eur")).isZero();
    }

    @Test
    @DisplayName("le franc CFA se transmet tel quel, jamais multiplié par cent")
    void francCfa() {
        // C'est le defaut que cette classe existe pour empecher : avec l'ancien calcul, une
        // formation reglee 328 000 XAF partait chez Stripe pour 32 800 000 XAF.
        assertThat(MontantStripe.versPlusPetiteUnite(new BigDecimal("328000"), "xaf"))
                .isEqualTo(328_000);
        assertThat(MontantStripe.versPlusPetiteUnite(new BigDecimal("500"), "xof"))
                .isEqualTo(500);
    }

    @Test
    @DisplayName("les autres devises sans subdivision suivent la même règle")
    void autresDevisesSansDecimale() {
        assertThat(MontantStripe.versPlusPetiteUnite(new BigDecimal("1200"), "jpy")).isEqualTo(1200);
        assertThat(MontantStripe.versPlusPetiteUnite(new BigDecimal("1200"), "krw")).isEqualTo(1200);
    }

    @Test
    @DisplayName("une devise inconnue est traitée comme ayant deux décimales")
    void deviseInconnue() {
        // Le cas general : la quasi-totalite des devises se divise en centiemes. Ce repli evite
        // qu'une devise ajoutee par Stripe demain fasse echouer un paiement en euros.
        assertThat(MontantStripe.versPlusPetiteUnite(new BigDecimal("10.50"), "cad")).isEqualTo(1050);
    }

    @Test
    @DisplayName("la casse et les espaces du code devise sont tolérés")
    void normalisation() {
        assertThat(MontantStripe.normaliser(" EUR ")).isEqualTo("eur");
        assertThat(MontantStripe.versPlusPetiteUnite(new BigDecimal("1.00"), " XAF ")).isEqualTo(1);
    }

    @Test
    @DisplayName("un code devise qui n'en est pas un est refusé")
    void deviseInvalide() {
        for (String invalide : new String[]{null, "", "e", "euro", "12 €"}) {
            assertThatThrownBy(() -> MontantStripe.normaliser(invalide))
                    .as("code %s", invalide)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("ISO 4217");
        }
    }

    @Test
    @DisplayName("une devise à trois décimales est refusée, pas approximée")
    void troisDecimales() {
        assertThatThrownBy(() -> MontantStripe.versPlusPetiteUnite(new BigDecimal("10.500"), "kwd"))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("multiple de dix");
    }

    @Test
    @DisplayName("un montant absent ou négatif est refusé")
    void montantInvalide() {
        assertThatThrownBy(() -> MontantStripe.versPlusPetiteUnite(null, "eur"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MontantStripe.versPlusPetiteUnite(new BigDecimal("-1.00"), "eur"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("un montant plus précis que la devise est arrondi, jamais tronqué")
    void arrondi() {
        // L'ancien calcul finissait par longValue(), qui tronque : 10,999 € donnait 1099
        // centimes, soit un centime offert a chaque vente. L'arrondi demi-superieur donne 1100.
        assertThat(MontantStripe.versPlusPetiteUnite(new BigDecimal("10.999"), "eur")).isEqualTo(1100);
        assertThat(MontantStripe.versPlusPetiteUnite(new BigDecimal("327978.5"), "xaf"))
                .isEqualTo(327_979);
    }
}

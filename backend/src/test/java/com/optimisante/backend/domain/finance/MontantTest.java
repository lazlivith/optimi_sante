package com.optimisante.backend.domain.finance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Le type qui porte une somme d'argent.
 *
 * <p>Ce qui est éprouvé n'est pas l'arithmétique, mais les erreurs qu'un {@code BigDecimal} nu
 * laissait passer : additionner deux devises, et envoyer à Stripe un montant converti dans la
 * mauvaise unité.</p>
 */
class MontantTest {

    @Test
    @DisplayName("additionner deux devises différentes est refusé, et le message le dit")
    void additionInterdite() {
        Montant euros = Montant.euros(new BigDecimal("100.00"));
        Montant francs = Montant.de(new BigDecimal("65596"), "XAF");

        assertThatThrownBy(() -> euros.plus(francs))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("devises différentes");
    }

    @Test
    @DisplayName("un montant est ramené à la précision de sa devise dès sa construction")
    void normalisation() {
        // Deux montants egaux doivent l'etre au sens d'equals, ce que des echelles differentes
        // empechaient : 10.0 et 10.00 sont la meme somme.
        assertThat(Montant.euros(new BigDecimal("10.0")))
                .isEqualTo(Montant.euros(new BigDecimal("10.00")));

        // Le franc CFA n'a pas de subdivision : la demie n'existe pas.
        assertThat(Montant.de(new BigDecimal("327978.5"), "XAF").valeur())
                .isEqualByComparingTo("327979");
    }

    @Test
    @DisplayName("l'euro part chez Stripe en centimes, le franc CFA tel quel")
    void versPlusPetiteUnite() {
        assertThat(Montant.euros(new BigDecimal("19.99")).versPlusPetiteUnite()).isEqualTo(1999);
        // Le defaut que tout ceci existe pour empecher : 328 000 XAF ne doivent pas devenir
        // 32 800 000.
        assertThat(Montant.de(new BigDecimal("328000"), "XAF").versPlusPetiteUnite())
                .isEqualTo(328_000);
    }

    @Test
    @DisplayName("le passage par Stripe conserve le montant")
    void allerRetour() {
        Montant euros = Montant.euros(new BigDecimal("49.90"));
        assertThat(Montant.depuisPlusPetiteUnite(euros.versPlusPetiteUnite(), Devise.REFERENCE))
                .isEqualTo(euros);

        Montant francs = Montant.de(new BigDecimal("328000"), "XAF");
        assertThat(Montant.depuisPlusPetiteUnite(francs.versPlusPetiteUnite(), Devise.de("XAF")))
                .isEqualTo(francs);
    }

    @Test
    @DisplayName("une devise à trois décimales est refusée, pas approximée")
    void troisDecimales() {
        assertThatThrownBy(() -> Montant.de(new BigDecimal("10.500"), "KWD").versPlusPetiteUnite())
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("multiple de dix");
    }

    @Test
    @DisplayName("les opérations gardent la devise")
    void operations() {
        Montant a = Montant.euros(new BigDecimal("10.00"));
        Montant b = Montant.euros(new BigDecimal("2.50"));

        assertThat(a.plus(b).valeur()).isEqualByComparingTo("12.50");
        assertThat(a.moins(b).valeur()).isEqualByComparingTo("7.50");
        assertThat(a.fois(3).valeur()).isEqualByComparingTo("30.00");
        assertThat(a.plus(b).devise()).isEqualTo(Devise.REFERENCE);
    }

    @Test
    @DisplayName("un code devise qui n'en est pas un est refusé")
    void deviseInvalide() {
        for (String invalide : new String[]{null, "", "e", "euro"}) {
            assertThatThrownBy(() -> Devise.de(invalide))
                    .as("code %s", invalide)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("ISO 4217");
        }
    }

    @Test
    @DisplayName("la casse et les espaces du code devise sont tolérés")
    void normalisationDevise() {
        assertThat(Devise.de(" eur ")).isEqualTo(Devise.REFERENCE);
        assertThat(Devise.de("xaf").decimales()).isZero();
        assertThat(Devise.REFERENCE.codeStripe()).isEqualTo("eur");
    }
}

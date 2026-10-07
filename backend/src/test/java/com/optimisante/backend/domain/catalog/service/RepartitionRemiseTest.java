package com.optimisante.backend.domain.catalog.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La répartition d'une remise sur des lignes de taux différents.
 *
 * <p>Ce qui est éprouvé ici est une propriété comptable, pas une formule : <b>le total hors
 * taxes augmenté de la taxe doit égaler le montant réellement réglé</b>. Tant que la remise
 * n'était pas répartie, l'écart valait exactement la remise — et il s'imprimait sur le reçu.</p>
 */
class RepartitionRemiseTest {

    /* Sans service de parametres : ces tests ne touchent pas l'interrupteur d'affichage. */
    private final ServiceTva service = new ServiceTva(null);

    private static ServiceTva.LigneTaxable ligne(String ttc, String taux) {
        return new ServiceTva.LigneTaxable(new BigDecimal(ttc), new BigDecimal(taux));
    }

    @Test
    @DisplayName("sans remise, les lignes ne bougent pas")
    void sansRemise() {
        List<ServiceTva.LigneTaxable> lignes = List.of(ligne("100.00", "20.00"));
        assertThat(service.repartirRemise(lignes, null)).isEqualTo(lignes);
        assertThat(service.repartirRemise(lignes, BigDecimal.ZERO)).isEqualTo(lignes);
    }

    @Test
    @DisplayName("la remise frappe les deux taux, chacun à hauteur de son poids")
    void auProrata() {
        // 800 EUR a 5,5 % et 200 EUR a 20 % : une remise de 100 EUR en retire 80 au premier
        // et 20 au second. L'imputer en entier sur l'un fausserait les deux bases.
        var apres = service.repartirRemise(
                List.of(ligne("800.00", "5.50"), ligne("200.00", "20.00")),
                new BigDecimal("100.00"));

        assertThat(apres.get(0).montantTtc()).isEqualByComparingTo("720.00");
        assertThat(apres.get(1).montantTtc()).isEqualByComparingTo("180.00");
    }

    @Test
    @DisplayName("hors taxes plus taxe égale le montant réellement réglé")
    void laProprieteQuiCompte() {
        // C'est le defaut que cette repartition corrige : sans elle, le recu annoncait un
        // hors-taxes et une taxe calcules sur 1 000 EUR alors que le client en avait regle 900.
        BigDecimal remise = new BigDecimal("100.00");
        BigDecimal regle = new BigDecimal("1000.00").subtract(remise);

        var ventilation = service.ventiler(service.repartirRemise(
                List.of(ligne("800.00", "5.50"), ligne("200.00", "20.00")), remise));

        BigDecimal ht = ventilation.stream().map(ServiceTva.Ventilation::ht)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal taxe = ventilation.stream().map(ServiceTva.Ventilation::taxe)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertThat(ht.add(taxe)).isEqualByComparingTo(regle);
    }

    @Test
    @DisplayName("une remise supérieure au panier ne rend pas de base négative")
    void remiseExcessive() {
        // Ne devrait pas arriver, mais ne doit surtout pas produire un recu annoncant une
        // taxe negative.
        var apres = service.repartirRemise(
                List.of(ligne("100.00", "20.00")), new BigDecimal("500.00"));

        assertThat(apres.get(0).montantTtc()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("un panier à taux unique se réduit exactement de la remise")
    void tauxUnique() {
        var apres = service.repartirRemise(
                List.of(ligne("120.00", "20.00"), ligne("80.00", "20.00")),
                new BigDecimal("50.00"));

        BigDecimal total = apres.stream().map(ServiceTva.LigneTaxable::montantTtc)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(total).isEqualByComparingTo("150.00");
    }
}

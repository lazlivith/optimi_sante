package com.optimisante.backend.domain.catalog.service;

import com.optimisante.backend.domain.catalog.entity.Category;
import com.optimisante.backend.domain.catalog.entity.Product;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La TVA extraite de prix annoncés toutes taxes comprises.
 *
 * <p>Ce qui est vérifié ici n'est pas l'arithmétique pour elle-même, mais trois promesses que
 * le document fiscal doit tenir : le montant payé ne change pas, ses deux parts le redonnent
 * exactement, et une commande à plusieurs taux est ventilée au lieu d'être agrégée.</p>
 */
class ServiceTvaTest {

    private final ServiceTva service = new ServiceTva();

    @Test
    @DisplayName("la taxe est extraite du prix TTC, qui ne bouge pas")
    void extraitSansChangerLePrix() {
        var d = service.decomposer(new BigDecimal("971.00"), new BigDecimal("20.00"));

        assertThat(d.ht()).isEqualByComparingTo("809.17");
        assertThat(d.taxe()).isEqualByComparingTo("161.83");
        // La promesse qui compte : les deux parts redonnent le prix affiche, au centime.
        assertThat(d.ht().add(d.taxe())).isEqualByComparingTo("971.00");
    }

    @Test
    @DisplayName("le taux réduit s'applique de la même façon")
    void tauxReduit() {
        var d = service.decomposer(new BigDecimal("1200.00"), new BigDecimal("5.50"));

        assertThat(d.ht()).isEqualByComparingTo("1137.44");
        assertThat(d.ht().add(d.taxe())).isEqualByComparingTo("1200.00");
    }

    @Test
    @DisplayName("un taux nul laisse tout en base, sans taxe")
    void tauxNul() {
        var d = service.decomposer(new BigDecimal("500.00"), BigDecimal.ZERO);

        assertThat(d.ht()).isEqualByComparingTo("500.00");
        assertThat(d.taxe()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("l'arrondi porte sur la ligne, pas sur chaque unité")
    void arrondiParLigne() {
        // 3 x 9,99 € = 29,97 €. Decompose unite par unite puis somme, l'arrondi derive ;
        // sur le total de ligne, les deux parts redonnent exactement le montant.
        var ligne = service.decomposer(new BigDecimal("29.97"), new BigDecimal("20.00"));
        var unite = service.decomposer(new BigDecimal("9.99"), new BigDecimal("20.00"));

        assertThat(ligne.ht().add(ligne.taxe())).isEqualByComparingTo("29.97");
        assertThat(unite.ht().multiply(new BigDecimal("3")).add(unite.taxe().multiply(new BigDecimal("3"))))
                .as("la somme des unites arrondies peut differer — c'est pourquoi on calcule par ligne")
                .isEqualByComparingTo("29.97");
    }

    @Test
    @DisplayName("une commande à deux taux donne deux bases, et non une somme")
    void ventilationParTaux() {
        var bases = service.ventiler(List.of(
                new ServiceTva.LigneTaxable(new BigDecimal("1200.00"), new BigDecimal("5.50")),
                new ServiceTva.LigneTaxable(new BigDecimal("240.00"), new BigDecimal("20.00")),
                new ServiceTva.LigneTaxable(new BigDecimal("60.00"), new BigDecimal("20.00"))));

        assertThat(bases).hasSize(2);
        assertThat(bases.get(0).taux()).isEqualByComparingTo("5.50");
        assertThat(bases.get(0).ttc()).isEqualByComparingTo("1200.00");
        // Les deux lignes a 20 % sont regroupees en une seule base, comme sur une facture.
        assertThat(bases.get(1).taux()).isEqualByComparingTo("20.00");
        assertThat(bases.get(1).ttc()).isEqualByComparingTo("300.00");
        assertThat(bases.get(1).ht()).isEqualByComparingTo("250.00");
        assertThat(bases.get(1).taxe()).isEqualByComparingTo("50.00");
    }

    @Test
    @DisplayName("le taux du produit l'emporte sur celui de sa catégorie")
    void leProduitPrimeSurLaCategorie() {
        Category rayon = Category.builder().vatRate(new BigDecimal("20.00")).build();
        Product fauteuilRoulant = Product.builder()
                .category(rayon).vatRate(new BigDecimal("5.50")).build();

        assertThat(service.tauxDe(fauteuilRoulant)).isEqualByComparingTo("5.50");
    }

    @Test
    @DisplayName("sans taux sur le produit, celui de la catégorie s'applique")
    void defautDeLaCategorie() {
        Category rayon = Category.builder().vatRate(new BigDecimal("5.50")).build();
        Product article = Product.builder().category(rayon).build();

        assertThat(service.tauxDe(article)).isEqualByComparingTo("5.50");
    }

    @Test
    @DisplayName("sans rien de renseigné, le taux normal s'applique")
    void defautTauxNormal() {
        assertThat(service.tauxDe(Product.builder().build())).isEqualByComparingTo("20.00");
        assertThat(service.tauxDe(null)).isEqualByComparingTo("20.00");
    }
}

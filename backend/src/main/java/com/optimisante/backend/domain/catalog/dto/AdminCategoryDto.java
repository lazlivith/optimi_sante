package com.optimisante.backend.domain.catalog.dto;

import java.util.UUID;

/**
 * Catégorie vue depuis l'administration, avec le nombre de produits qu'elle contient.
 *
 * <p>Distincte de {@link CategoryResponseDto}, qui sert la boutique et porte l'arborescence.
 * Ici, le compteur n'est pas décoratif : le catalogue compte 223 catégories dont 93 sans
 * aucun produit. Sans ce chiffre, l'administrateur choisit un filtre au hasard et tombe sur
 * une liste vide sans comprendre pourquoi.</p>
 */
public record AdminCategoryDto(
        UUID id,
        String name,
        String slug,
        /** Marge appliquée au prix d'achat lors d'un import ; nulle = celle du fournisseur s'applique. */
        java.math.BigDecimal marginRate,
        /**
         * Taux de TVA de la famille, en pourcentage.
         *
         * <p>{@code null} ne veut pas dire « zéro » : il veut dire <b>non examiné</b>, et le
         * produit retombe alors sur le taux normal de 20 % — ce qui est la règle légale pour
         * le matériel médical, et non un défaut technique. Les confondre ferait croire à un
         * arbitrage là où il n'y en a pas eu (V68).</p>
         */
        java.math.BigDecimal vatRate,
        /** Le comptable a signalé ce taux comme à confirmer sur la liste officielle (V68). */
        boolean vatRateAVerifier,
        long productCount
) {}

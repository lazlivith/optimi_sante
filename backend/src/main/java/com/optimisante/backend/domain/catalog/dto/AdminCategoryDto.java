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
        long productCount
) {}

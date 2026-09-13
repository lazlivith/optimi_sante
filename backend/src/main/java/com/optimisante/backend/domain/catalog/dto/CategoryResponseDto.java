package com.optimisante.backend.domain.catalog.dto;

import java.util.List;
import java.util.UUID;

/**
 * Catégorie telle que la boutique la présente.
 *
 * @param productCount produits achetables aujourd'hui — actifs et non supprimés. Le front
 *                     s'en sert pour n'exposer que des rayons qui mènent quelque part : 85
 *                     des 211 catégories sont vides, et une vignette vers une page sans
 *                     produit est une impasse.
 * @param imageUrl     photo d'un vrai produit du rayon, ou {@code null} si le rayon est vide.
 *                     La table {@code categories} ne porte pas d'illustration ; plutôt que
 *                     d'en inventer une, on montre ce que le rayon contient réellement.
 */
public record CategoryResponseDto(
        UUID id,
        String name,
        String slug,
        long productCount,
        String imageUrl,
        List<CategoryResponseDto> subcategories
) {}

package com.optimisante.backend.domain.catalog.repository;

import java.util.UUID;

/**
 * Projection native : catégorie + nombre de produits.
 *
 * <p>Native et non JPQL, pour la même raison que {@link AdminProductRow} : {@code Product}
 * porte {@code @SQLRestriction("deleted_at IS NULL AND is_active = true")}, qu'Hibernate
 * applique à toute requête touchant l'entité — jointures comprises. Un comptage en JPQL
 * ignorerait donc les produits désactivés, et une catégorie n'en contenant que de tels
 * produits s'afficherait comme vide alors qu'elle ne l'est pas. L'administrateur la croirait
 * inutilisée et pourrait la supprimer.</p>
 */
public interface CategoryUsageRow {
    UUID getId();
    String getName();
    String getSlug();
    long getProductCount();
}

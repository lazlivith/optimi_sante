package com.optimisante.backend.domain.catalog.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Projection native (pas l'entité Product) utilisée par les requêtes admin qui doivent
 * ignorer @SQLRestriction("deleted_at IS NULL AND is_active = true") — sans cette projection,
 * Hibernate applique la restriction même aux requêtes natives dès que le résultat est mappé
 * sur l'entité Product, rendant impossible la consultation des produits désactivés par l'admin.
 */
public interface AdminProductRow {
    UUID getId();
    String getSku();
    String getName();
    String getSlug();
    String getDescription();
    BigDecimal getBasePrice();
    Integer getStockQuantity();
    Integer getStockThreshold();
    Boolean getIsQuoteOnly();
    Boolean getIsActive();
    String getImageUrl();
    UUID getCategoryId();
    UUID getTrainingId();
    BigDecimal getPromoPrice();
    /**
     * Dates de promotion, en {@link Instant} et non en {@code OffsetDateTime}.
     *
     * <p>La requete est NATIVE : le pilote JDBC remonte un {@code timestamptz} sous forme
     * d'{@code Instant}, et Spring Data ne sait pas le convertir tout seul vers un autre type
     * temporel — il repond <i>« Cannot project java.time.Instant to java.time.OffsetDateTime »</i>.
     *
     * <p><b>Le defaut est reste invisible tant qu'aucun produit n'avait de promotion.</b> Une
     * colonne nulle ne declenche aucune conversion : la projection paraissait donc juste. Des
     * qu'une seule ligne de la page consultee porte une date, la requete entiere echoue et
     * l'ecran affiche « 0 produit ». La conversion vers {@code OffsetDateTime} se fait
     * explicitement dans le mappeur, a la frontiere du DTO.
     */
    Instant getPromoStartsAt();
    Instant getPromoEndsAt();
}

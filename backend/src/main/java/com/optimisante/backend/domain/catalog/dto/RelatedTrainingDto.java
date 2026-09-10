package com.optimisante.backend.domain.catalog.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Formation associee a un equipement, telle qu'elle apparait sur la fiche produit.
 *
 * <p>Volontairement reduite : la fiche produit annonce l'offre et renvoie vers la formation,
 * elle ne la decrit pas. Y verser la description complete alourdirait chaque produit du
 * catalogue pour un encart de quatre lignes.</p>
 */
public record RelatedTrainingDto(
        UUID id,
        String title,
        String slug,
        BigDecimal price,
        Integer durationDays
) {}

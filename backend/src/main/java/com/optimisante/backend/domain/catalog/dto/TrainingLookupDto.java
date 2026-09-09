package com.optimisante.backend.domain.catalog.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Formation telle qu'elle apparait dans le selecteur du formulaire produit.
 *
 * <p>Reduite a ce qu'il faut pour choisir : l'administration du negoce n'a pas a connaitre le
 * contenu pedagogique, le partenaire ou les candidatures. {@code alreadyLinked} evite de
 * proposer une formation deja rattachee a un autre produit — la relation est 1:1, et la base
 * refuserait l'enregistrement avec une violation de contrainte peu parlante.</p>
 */
public record TrainingLookupDto(
        UUID id,
        String title,
        String institutionName,
        BigDecimal price,
        Integer durationDays,
        boolean alreadyLinked
) {}

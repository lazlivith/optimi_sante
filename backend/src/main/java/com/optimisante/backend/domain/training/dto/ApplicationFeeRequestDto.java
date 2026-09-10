package com.optimisante.backend.domain.training.dto;

import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;

/**
 * Fixe les frais de dossier d'une formation.
 *
 * <p>{@code null} retire le tarif propre et fait revenir la formation a la valeur globale.
 * C'est une valeur distincte de zero, qui signifierait « candidature gratuite ».</p>
 */
public record ApplicationFeeRequestDto(
        @DecimalMin(value = "0.00", message = "Les frais de dossier ne peuvent pas être négatifs.")
        BigDecimal applicationFee
) {}

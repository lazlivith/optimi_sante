package com.optimisante.backend.domain.partnership.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Ce que l'administrateur décide au moment d'approuver une demande de partenariat.
 *
 * <p>Les deux champs sont facultatifs : l'ancien appel, sans corps, reste valable et se
 * comporte comme avant — aucune remise, aucune conversion de compte autorisée.</p>
 */
@Data
public class PartnershipApprovalRequestDto {

    /**
     * Remise accordée à l'établissement sur la boutique, en pourcentage.
     *
     * <p>Un partenaire est aussi un client : il achète du matériel. Le taux est saisi
     * établissement par établissement, et non déduit d'un barème — c'est une négociation
     * commerciale, pas une règle. Absent, il vaut zéro : un partenaire sans accord tarifaire
     * paie le prix public, ce qui est le comportement d'avant.</p>
     */
    @DecimalMin(value = "0.00", message = "La remise ne peut pas être négative.")
    @DecimalMax(value = "100.00", message = "La remise ne peut pas dépasser 100 %.")
    private BigDecimal b2bDiscountRate;

    /**
     * L'administrateur a-t-il confirmé la conversion d'un compte existant ?
     *
     * <p>Un compte n'a qu'un rôle. Approuver une demande dont l'adresse appartient déjà à un
     * client ou à un médecin convertit ce compte, et lui retire l'espace qu'il avait. Ce
     * n'est pas une décision à prendre en silence : sans cette confirmation explicite,
     * l'approbation est refusée et le service dit précisément ce qui serait perdu.</p>
     */
    private boolean confirmerConversion;
}

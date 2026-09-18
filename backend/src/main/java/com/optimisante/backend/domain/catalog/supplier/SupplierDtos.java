package com.optimisante.backend.domain.catalog.supplier;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Charges utiles des fournisseurs et de leurs imports.
 *
 * <p>Regroupées dans un seul fichier, comme {@code OfficialDocumentDtos} et {@code InterviewDtos}.</p>
 */
public final class SupplierDtos {

    private SupplierDtos() {}

    /** Code facultatif : dérivé de la raison sociale quand il n'est pas saisi. */
    public record SupplierRequest(
            @Size(max = 40) String code,
            @NotBlank(message = "La raison sociale est obligatoire.") @Size(max = 255) String companyName,
            @Size(max = 50) String taxId,
            @Size(max = 150) String contactName,
            @Email(message = "L'adresse e-mail du contact est mal formée.") @Size(max = 255) String contactEmail,
            @Size(max = 30) String contactPhone,
            BigDecimal commissionRate,
            String notes) {
    }

    public record SupplierView(
            UUID id, String code, String companyName, String taxId, String contactName, String contactEmail,
            String contactPhone, BigDecimal commissionRate, String notes, boolean active,
            long productCount, OffsetDateTime createdAt) {
    }

    /**
     * Un import, de l'analyse au résultat.
     *
     * @param motifs lignes refusées ou écartées, telles qu'affichées à l'écran
     */
    public record ImportView(
            UUID id, UUID supplierId, String fileName, String status, int totalRows, int toCreate, int toUpdate,
            int ignoredRows, int errorRows, int processedRows, int createdCount, int updatedCount, int imageCount,
            List<String> motifs, boolean motifsTronques, String failureReason,
            OffsetDateTime createdAt, OffsetDateTime confirmedAt, OffsetDateTime finishedAt) {
    }
}

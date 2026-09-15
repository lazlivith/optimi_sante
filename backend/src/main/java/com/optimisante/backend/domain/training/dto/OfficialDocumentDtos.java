package com.optimisante.backend.domain.training.dto;

import com.optimisante.backend.domain.training.entity.OfficialDocumentCategory;
import com.optimisante.backend.domain.training.entity.OfficialDocumentIssuer;
import com.optimisante.backend.domain.training.entity.OfficialDocumentStatus;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Charges utiles des documents officiels et du coffre-fort par dossier.
 *
 * <p>Regroupées dans un seul fichier, comme {@link InterviewDtos} et {@link ServiceOptionDtos}.</p>
 */
public final class OfficialDocumentDtos {

    private OfficialDocumentDtos() {}

    /** Un document officiel, vu par l'administration ou le CHU. */
    public record OfficialDocumentView(
            UUID id, OfficialDocumentCategory category, String categoryLabel, boolean departureKit,
            String title, String fileName, OfficialDocumentIssuer issuer, OfficialDocumentStatus status,
            String rejectionReason, OffsetDateTime uploadedAt, OffsetDateTime reviewedAt) {
    }

    public record ReviewRequest(Boolean accept, String reason) {
    }

    /** État d'une ligne du coffre-fort, du point de vue du médecin. */
    public enum VaultEntryState {
        /** Le document existe et le médecin peut l'ouvrir. */
        AVAILABLE,
        /** Le document existe mais s'ouvre plus tard dans le parcours (acompte, visa, solde). */
        LOCKED,
        /** Le document n'existe pas encore : il sera émis à une étape à venir. */
        UPCOMING
    }

    /**
     * Une ligne du coffre-fort.
     *
     * @param downloadType type attendu par {@code /documents/{type}/{id}/download} ; nul si rien à ouvrir
     */
    public record VaultEntry(
            String key, String title, String categoryLabel, String issuerLabel, OffsetDateTime date,
            VaultEntryState state, String reason, String downloadType, UUID downloadId) {
    }

    public record VaultDossier(
            UUID enrollmentId, String trainingTitle, String institutionName, LocalDate sessionStart,
            String status, List<VaultEntry> entries) {
    }
}

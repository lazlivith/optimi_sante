package com.optimisante.backend.domain.document.dto;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class DocumentItemDto {
    private UUID id;
    private String title;
    private String type; // CONVENTION, INVOICE, RECEIPT
    /**
     * Libellé lisible du type, calculé par le serveur.
     *
     * <p>Sans lui, chaque écran retraduisait le code avec sa propre table, et les tables
     * divergeaient de l'énumération — un dossier affichait « INTERVIEW_CONVOCATION » à
     * l'administrateur chargé de le vérifier.</p>
     */
    private String typeLabel;
    private OffsetDateTime date;
    private String status;
    private String documentKey;
}

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
    private OffsetDateTime date;
    private String status;
    private String documentKey;
}

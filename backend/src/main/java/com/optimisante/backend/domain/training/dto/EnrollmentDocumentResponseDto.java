package com.optimisante.backend.domain.training.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class EnrollmentDocumentResponseDto {
    private UUID id;
    private String documentType;
    private Boolean isVerified;
    private Instant uploadedAt;
}

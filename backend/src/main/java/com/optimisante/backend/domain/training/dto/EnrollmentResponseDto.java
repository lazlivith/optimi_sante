package com.optimisante.backend.domain.training.dto;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class EnrollmentResponseDto {
    private UUID id;
    private String status;
    /** INTERNATIONAL_VISA ou LOCAL_FRANCE : l'interface en deduit le parcours a afficher. */
    private String registrationType;
    private String rppsNumber;
    private String diplomaUrl;
    private String medicalBoardRegistrationUrl;
    private String passportUrl;
    private OffsetDateTime submittedAt;
}

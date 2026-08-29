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
    private String diplomaUrl;
    private String medicalBoardRegistrationUrl;
    private String passportUrl;
    private OffsetDateTime submittedAt;
}

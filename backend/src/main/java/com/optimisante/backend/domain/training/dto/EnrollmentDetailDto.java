package com.optimisante.backend.domain.training.dto;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class EnrollmentDetailDto {
    private UUID id;
    private String status;
    private String trainingTitle;
    private String doctorName;
    private String doctorEmail;
    private OffsetDateTime submittedAt;
    private String diplomaUrl;
    private String medicalBoardRegistrationUrl;
    private String passportUrl;
    private String conventionS3Key;
    private String attestationS3Key;
}

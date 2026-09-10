package com.optimisante.backend.domain.training.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Vue d'une formation pour l'admin, avec l'identité du partenaire qui l'a soumise. */
@Data
@Builder
public class AdminTrainingResponseDto {
    private UUID id;
    private String title;
    private String medicalSpecialty;
    private String description;
    private Integer durationDays;
    private Boolean isLongStay;
    private BigDecimal price;

    /** Frais de dossier propres a la formation. {@code null} = valeur globale appliquee. */
    private BigDecimal applicationFee;
    private Boolean isPublished;
    private String approvalStatus;
    private String rejectionReason;
    private String brochureUrl;
    private String imageUrl;
    private String videoUrl;
    private String partnerInstitutionName;
    private String partnerContactEmail;
    private OffsetDateTime createdAt;
}

package com.optimisante.backend.domain.training.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Vue détaillée d'une formation pour son propriétaire (le partenaire CHU qui l'a créée). */
@Data
@Builder
public class PartnerTrainingResponseDto {
    private UUID id;
    private String title;
    private String slug;
    private String medicalSpecialty;
    private String description;
    private Integer durationDays;
    private Boolean isLongStay;
    private BigDecimal price;
    private Boolean isPublished;
    private String approvalStatus;
    private String rejectionReason;
    private String brochureUrl;
    private String imageUrl;
    private String videoUrl;
    private OffsetDateTime createdAt;
}

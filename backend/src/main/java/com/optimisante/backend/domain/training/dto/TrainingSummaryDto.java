package com.optimisante.backend.domain.training.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class TrainingSummaryDto {
    private UUID id;
    private String title;
    private String medicalSpecialty;
    private String description;
    private Integer durationDays;
    private Boolean isLongStay;
    private String location;
    private String brochureUrl;
    private String imageUrl;
    private String videoUrl;
    private BigDecimal price;
}

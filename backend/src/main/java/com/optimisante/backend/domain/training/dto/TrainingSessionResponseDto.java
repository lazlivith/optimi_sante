package com.optimisante.backend.domain.training.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class TrainingSessionResponseDto {
    private UUID id;
    private UUID trainingId;
    private String trainingTitle;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer capacity;
    private Integer availableSeats;
    private String location;
    private BigDecimal price;
    private String status;
    private OffsetDateTime createdAt;
}

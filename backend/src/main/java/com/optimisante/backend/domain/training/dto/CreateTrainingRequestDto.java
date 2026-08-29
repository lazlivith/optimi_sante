package com.optimisante.backend.domain.training.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record CreateTrainingRequestDto(
        @NotBlank String title,
        @NotBlank String medicalSpecialty,
        @NotBlank String description,
        @NotNull @Positive Integer durationDays,
        @NotNull Boolean isLongStay,
        @NotNull @Positive BigDecimal price
) {
}

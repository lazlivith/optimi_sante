package com.optimisante.backend.domain.training.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateSessionRequestDto(
        @NotNull UUID trainingId,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @NotNull @Min(1) Integer capacity,
        @NotBlank String location,
        @NotNull BigDecimal price
) {
}

package com.optimisante.backend.domain.training.dto;

import jakarta.validation.constraints.NotBlank;

public record TrainingRejectRequestDto(
        @NotBlank String reason
) {
}

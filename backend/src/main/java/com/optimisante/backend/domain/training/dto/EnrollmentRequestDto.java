package com.optimisante.backend.domain.training.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record EnrollmentRequestDto(
    @NotNull(message = "L'ID de la session est obligatoire")
    UUID sessionId
) {}

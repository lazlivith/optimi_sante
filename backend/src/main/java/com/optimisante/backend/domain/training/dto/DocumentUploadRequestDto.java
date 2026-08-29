package com.optimisante.backend.domain.training.dto;

public record DocumentUploadRequestDto(
    String diplomaUrl,
    String medicalBoardRegistrationUrl,
    String passportUrl
) {}

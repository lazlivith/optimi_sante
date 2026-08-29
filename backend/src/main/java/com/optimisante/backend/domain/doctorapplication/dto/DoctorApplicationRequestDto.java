package com.optimisante.backend.domain.doctorapplication.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class DoctorApplicationRequestDto {

    @NotBlank
    private String tenantCode;

    @NotNull(message = "L'ID de la session est obligatoire")
    private UUID sessionId;

    @NotBlank
    @Email
    private String email;

    @NotBlank
    private String firstName;

    @NotBlank
    private String lastName;

    @NotBlank
    private String phoneWhatsapp;

    @NotBlank
    private String countryOfResidence;

    @NotBlank
    private String medicalSpecialty;

    private String medicalCouncilNumber;

    private String currentHospital;

    private String passportNumber;
}

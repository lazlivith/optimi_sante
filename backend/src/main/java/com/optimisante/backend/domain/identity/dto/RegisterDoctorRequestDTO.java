package com.optimisante.backend.domain.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RegisterDoctorRequestDTO {
    @NotBlank
    @Email
    private String email;

    @NotBlank
    private String password;

    @NotBlank
    private String tenantCode;

    // DoctorProfile specific fields
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

package com.optimisante.backend.domain.identity.dto;

public record UpdateProfileRequestDto(
        String firstName,
        String lastName,
        String phoneWhatsapp,
        String countryOfResidence,
        String medicalSpecialty,
        String medicalCouncilNumber,
        String currentHospital,
        String companyName,
        String siretFiness
) {
}

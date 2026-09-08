package com.optimisante.backend.domain.identity.dto;

import com.optimisante.backend.domain.identity.entity.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileDTO {
    private UUID id;
    private String email;
    private Role role;
    private String tenantCode;
    // Identité (médecin via DoctorProfile, autres rôles via User — V23)
    private String firstName;
    private String lastName;
    private String phoneWhatsapp;
    private String countryOfResidence;
    private String medicalSpecialty;
    private String medicalCouncilNumber;
    private String currentHospital;
    // Champs B2B
    private String companyName;
    /** Identifiant fiscal générique (ex-siretFiness, renommé en V23 pour l'international). */
    private String taxId;
    private String vatNumber;
    private String billingAddress;
    private String country;
    private String facilityType;
    private String contactName;
}

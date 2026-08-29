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
    // Champs Médecin
    private String firstName;
    private String lastName;
    private String phoneWhatsapp;
    private String countryOfResidence;
    private String medicalSpecialty;
    private String medicalCouncilNumber;
    private String currentHospital;
    // Champs B2B
    private String companyName;
    private String siretFiness;
    private String vatNumber;
    private String billingAddress;
}

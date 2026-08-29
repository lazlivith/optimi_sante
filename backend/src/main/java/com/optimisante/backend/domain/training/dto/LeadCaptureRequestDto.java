package com.optimisante.backend.domain.training.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeadCaptureRequestDto {

    @NotBlank(message = "L'email est obligatoire")
    @Email(message = "Format d'email invalide")
    private String email;

    @NotBlank(message = "Le prénom est obligatoire")
    private String firstName;

    @NotBlank(message = "Le nom est obligatoire")
    private String lastName;

    @NotBlank(message = "Le numéro de téléphone (WhatsApp) est obligatoire")
    private String phoneWhatsapp;

    @NotBlank(message = "Le pays est obligatoire")
    private String country;

    @NotBlank(message = "La spécialité est obligatoire")
    private String specialty;

    private String source;
}

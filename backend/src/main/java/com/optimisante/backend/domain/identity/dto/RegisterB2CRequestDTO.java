package com.optimisante.backend.domain.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterB2CRequestDTO {

    @NotBlank(message = "Le prénom est obligatoire")
    private String firstName;

    @NotBlank(message = "Le nom est obligatoire")
    private String lastName;

    @NotBlank(message = "L'email est obligatoire")
    @Email(message = "Format d'email invalide")
    private String email;

    @NotBlank(message = "Le mot de passe est obligatoire")
    @Size(min = 8, message = "Le mot de passe doit contenir au moins 8 caractères")
    private String password;

    /**
     * Tenant cible. Optionnel : le frontend peut l'omettre, le tenant par défaut
     * (FR_MAIN) est alors appliqué côté service — rétrocompatible avec les clients
     * existants qui continuent de l'envoyer.
     */
    private String tenantCode;
}

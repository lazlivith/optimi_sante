package com.optimisante.backend.domain.identity.dto;

import com.optimisante.backend.domain.identity.entity.FacilityType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Inscription d'un client professionnel (clinique, hôpital, cabinet, laboratoire,
 * revendeur...). Portée internationale : pas de champ franco-centré, l'identifiant
 * légal est générique et le pays de domiciliation est explicite.
 *
 * Le compte est créé immédiatement actif avec le rôle CLIENT_B2B : il peut donc
 * faire une demande de devis (quote-request) sans validation préalable.
 */
@Data
public class RegisterB2BRequestDTO {

    @NotBlank(message = "La raison sociale est obligatoire")
    private String companyName;

    @NotNull(message = "Le type d'établissement est obligatoire")
    private FacilityType facilityType;

    @NotBlank(message = "Le pays est obligatoire")
    private String country;

    /** Numéro d'immatriculation légale : Tax ID, N° TVA, ICE, SIRET, Registration No. */
    @NotBlank(message = "L'identifiant fiscal ou numéro d'immatriculation est obligatoire")
    private String taxId;

    @NotBlank(message = "Le nom du contact est obligatoire")
    private String contactName;

    /** Téléphone professionnel, indicatif international accepté (+212, +33, +1...). */
    @NotBlank(message = "Le téléphone professionnel est obligatoire")
    private String phone;

    @NotBlank(message = "L'email est obligatoire")
    @Email(message = "Format d'email invalide")
    private String email;

    @NotBlank(message = "Le mot de passe est obligatoire")
    @Size(min = 8, message = "Le mot de passe doit contenir au moins 8 caractères")
    private String password;

    /**
     * Tenant cible. Optionnel : le tenant par défaut (FR_MAIN) est appliqué côté service
     * s'il n'est pas fourni — rétrocompatible avec les clients qui l'envoient déjà.
     */
    private String tenantCode;

    /** Optionnel, conservé pour les structures qui souhaitent le préciser dès l'inscription. */
    private String vatNumber;
}

package com.optimisante.backend.domain.identity.entity;

/**
 * Type d'établissement d'un client professionnel (B2B).
 *
 * ⚠️ Toute valeur ajoutée ici DOIT l'être simultanément dans la contrainte SQL
 * `company_profiles_facility_type_check` (voir migration V23), sous peine de faire
 * échouer l'insertion en base — cf. entrée #40 du journal des erreurs.
 */
public enum FacilityType {
    CLINIC,           // Clinique privée
    HOSPITAL,         // Hôpital public
    MEDICAL_PRACTICE, // Cabinet médical
    LABORATORY,       // Laboratoire
    DISTRIBUTOR,      // Revendeur / Grossiste
    OTHER             // Autre
}

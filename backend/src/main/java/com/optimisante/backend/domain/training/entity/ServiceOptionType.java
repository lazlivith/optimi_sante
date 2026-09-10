package com.optimisante.backend.domain.training.entity;

/**
 * Nature d'un service organise par OptimiSante autour du sejour.
 *
 * <p>Ces prestations ne sont pas rendues par le CHU : c'est l'agence qui les monte et qui paie
 * ses prestataires. C'est ce qui justifie qu'elles reviennent integralement a la plateforme,
 * la ou les frais de formation se partagent avec l'etablissement.</p>
 *
 * ⚠️ Toute valeur ajoutee ici doit l'etre simultanement dans **deux** contraintes SQL :
 * {@code training_service_options_type_check} et {@code eso_type_check} (V43).
 */
public enum ServiceOptionType {
    /** Assurance du candidat pendant son sejour. */
    INSURANCE,
    /** Hebergement sur place. */
    HOUSING,
    /** Transferts et deplacements locaux. */
    TRANSPORT
}

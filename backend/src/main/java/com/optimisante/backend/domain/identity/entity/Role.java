package com.optimisante.backend.domain.identity.entity;

/**
 * Rôles de la plateforme.
 *
 * <p>L'administration est scindée en deux métiers sans recouvrement (V30) : le négoce
 * médical et la mobilité. La séparation est portée par le rôle, donc appliquée jusque dans
 * l'API — et non par la seule navigation, qui ne garantirait rien.</p>
 *
 * <p>Les droits ne se lisent pas ici mais dans les annotations composées
 * {@code @EcommerceAdmin}, {@code @MobilityAdmin} et {@code @PlatformAdmin} : répéter la
 * liste des rôles sur une trentaine d'endpoints garantirait un oubli tôt ou tard.</p>
 */
public enum Role {

    /** Gouvernance : accède aux deux univers et gère seul les comptes. */
    SUPER_ADMIN,

    /**
     * Administration héritée, antérieure à la scission.
     *
     * @deprecated conservé le temps de la bascule et accepté par les deux univers, afin
     *             qu'aucun compte existant ne perde son accès au déploiement (V30). La
     *             réaffectation vers {@link #ADMIN_ECOMMERCE} ou {@link #ADMIN_MOBILITE}
     *             est une décision d'organisation, faite compte par compte. Son retrait
     *             fera l'objet d'une migration dédiée.
     */
    @Deprecated
    ADMIN,

    /** Négoce : catalogue, commandes, devis B2B, codes promo, finance des ventes. */
    ADMIN_ECOMMERCE,

    /** Mobilité : dossiers, formations, partenariats, reversements, emails. */
    ADMIN_MOBILITE,

    CLIENT_B2C,
    CLIENT_B2B,
    MEDECIN,
    CENTRE_FORMATION
}

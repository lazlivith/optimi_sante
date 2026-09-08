package com.optimisante.backend.domain.training.finance;

/**
 * Cycle d'un encaissement.
 *
 * Nom volontairement qualifié par son paquet : le domaine `orders` porte déjà un
 * PaymentStatus pour l'e-commerce, avec des valeurs différentes. Les deux ne doivent
 * jamais être confondus ni fusionnés.
 */
public enum PaymentStatus {
    PENDING,
    PAID,
    FAILED,
    REFUNDED
}

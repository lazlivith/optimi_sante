package com.optimisante.backend.domain.catalog.entity;

/**
 * Pourquoi le stock d'un produit a varié.
 *
 * <p>Le motif n'est pas décoratif : c'est lui qui permet de relire le journal et de répondre à
 * « d'où vient ce chiffre ». Une sortie pour vente et une correction à la main ne se lisent
 * pas de la même façon — la première est subie, la seconde est une décision qu'on peut
 * contester.</p>
 */
public enum MotifMouvementStock {

    /** Une commande payée. Le delta est négatif, et {@code orderId} porte la commande. */
    VENTE,

    /** Réception de marchandise. Le delta est positif. */
    REAPPROVISIONNEMENT,

    /**
     * Comptage physique : le stock est <b>posé</b> à la valeur constatée, et le delta vaut
     * l'écart avec ce que la base croyait. C'est le seul motif qui date l'inventaire du
     * produit, donc le seul qui rende son chiffre digne de confiance.
     */
    INVENTAIRE,

    /** Casse, perte, retour fournisseur, erreur de saisie : un ajustement assumé. */
    CORRECTION,

    /** Remise en stock après annulation ou remboursement d'une commande. Delta positif. */
    ANNULATION
}

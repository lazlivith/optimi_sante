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
    ANNULATION,

    /**
     * Quantité annoncée par un fournisseur lors d'un import de catalogue.
     *
     * <p><b>Ce n'est pas notre stock.</b> C'est une photographie de l'entrepôt de quelqu'un
     * d'autre, datée du jour de l'import, et elle ne refuse aucune commande. Elle est
     * journalisée tout de même : avant, l'import écrivait {@code stock_quantity} directement,
     * sans mouvement ni verrou — un inventaire compté le matin disparaissait sans trace à
     * l'import du soir. {@code importId} porte le job qui l'a écrite.</p>
     */
    MISE_A_JOUR_FOURNISSEUR
}

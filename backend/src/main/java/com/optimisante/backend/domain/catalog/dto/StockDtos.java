package com.optimisante.backend.domain.catalog.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Les formes échangées avec l'écran « Stock ». Regroupées ici parce qu'elles n'ont de sens
 * qu'ensemble : un produit, son historique, et le résumé qui les chapeaute.
 */
public final class StockDtos {

    private StockDtos() {
    }

    /**
     * Une ligne de l'écran.
     *
     * @param reserve      unités retenues par des réservations en cours, non encore vendues.
     * @param disponible   ce qu'un client peut réellement commander : stock moins réservations.
     * @param inventorieLe {@code null} si le produit n'a jamais été compté — son stock vaut
     *                     alors le défaut de l'import, pas un constat.
     */
    public record LigneStock(
            UUID productId,
            String nom,
            String slug,
            String categorie,
            int stock,
            int reserve,
            int disponible,
            int seuil,
            String etat,
            OffsetDateTime inventorieLe,
            boolean actif
    ) {
    }

    /** Le bandeau de tête : de quoi voir l'état du catalogue sans lire la liste. */
    public record ResumeStock(
            long produitsSuivis,
            long enRupture,
            long sousSeuil,
            long jamaisInventories,
            long unitesEnStock,
            long unitesReservees
    ) {
    }

    public record LigneMouvement(
            UUID id,
            UUID productId,
            String produit,
            int delta,
            int stockApres,
            String motif,
            UUID orderId,
            String auteur,
            String commentaire,
            OffsetDateTime date
    ) {
    }

    /** Saisie d'un mouvement depuis l'administration. */
    public record AjustementRequest(
            String motif,
            Integer delta,
            Integer quantiteConstatee,
            String commentaire
    ) {
    }

    /** Modification du seuil critique d'un produit. */
    public record SeuilRequest(Integer seuil) {
    }
}

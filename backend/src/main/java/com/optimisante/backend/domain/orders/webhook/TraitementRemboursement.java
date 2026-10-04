package com.optimisante.backend.domain.orders.webhook;

/**
 * Un parcours de paiement capable de reconnaitre un remboursement et de l'appliquer.
 *
 * <p><b>Pourquoi une interface plutot qu'une suite de « si ».</b> La plateforme encaisse sur
 * cinq motifs — commande, frais de dossier, acompte, solde, options de service — repartis sur
 * deux registres distincts. Un remboursement ne porte que l'identifiant du paiement : c'est
 * a chaque parcours de dire s'il le reconnait. Le meme decoupage sert deja a la confirmation
 * de paiement ({@code StripePaymentHandler}) ; ajouter un parcours ne doit toucher ni le
 * point d'entree du webhook, ni les parcours deja en production.</p>
 */
public interface TraitementRemboursement {

    /**
     * Applique le remboursement si ce parcours reconnait le paiement.
     *
     * @return {@code true} si le paiement a ete reconnu et traite ; {@code false} s'il releve
     *         d'un autre parcours, auquel cas l'aiguilleur poursuit sa recherche
     */
    boolean appliquer(RemboursementConstate remboursement);
}

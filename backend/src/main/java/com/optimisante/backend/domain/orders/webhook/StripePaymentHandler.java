package com.optimisante.backend.domain.orders.webhook;

import com.stripe.model.checkout.Session;

import java.util.UUID;

/**
 * Traitement d'un paiement Stripe confirmé, spécialisé par usage.
 *
 * <p>Stripe Checkout sert plusieurs parcours de la plateforme (commande e-commerce,
 * frais de dossier d'une candidature, frais de formation). Plutôt que de faire grossir
 * un {@code if/else} dans le contrôleur de webhook à chaque nouvel usage, chaque parcours
 * fournit son implémentation : ajouter un mode de paiement ne modifie plus le point
 * d'entrée, il ajoute un bean (principe ouvert/fermé).</p>
 *
 * <p>L'usage est porté par la métadonnée {@code payment_type} de la session. La métadonnée
 * historique {@code type} reste reconnue : des sessions créées avant cette refonte peuvent
 * encore être en cours de paiement au moment du déploiement.</p>
 */
public interface StripePaymentHandler {

    /** @param paymentType usage résolu depuis les métadonnées de la session, jamais null. */
    boolean supports(String paymentType);

    /**
     * @param referenceId identifiant métier porté par {@code client_reference_id}
     *                    (commande, candidature ou dossier selon l'usage)
     */
    void handle(UUID referenceId, Session session);
}

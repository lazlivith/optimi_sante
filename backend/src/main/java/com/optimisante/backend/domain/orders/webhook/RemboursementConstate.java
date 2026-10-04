package com.optimisante.backend.domain.orders.webhook;

import com.optimisante.backend.domain.finance.Montant;

/**
 * Un remboursement constaté chez Stripe, décrit indépendamment du parcours qu'il concerne.
 *
 * @param paymentIntentId  identifiant du paiement chez Stripe — le seul lien que l'événement
 *                         porte vers nos données, d'où qu'il vienne
 * @param montantRembourse montant <b>cumulé</b> rendu sur ce paiement. Stripe transmet le
 *                         cumul, et non le dernier versement : deux remboursements de 20 € puis
 *                         30 € donnent 50 €. C'est ce qui rend le traitement naturellement
 *                         idempotent — rejouer un événement réécrit la même valeur.
 * @param integral         vrai lorsque la totalité du paiement a été rendue
 */
public record RemboursementConstate(String paymentIntentId, Montant montantRembourse,
                                    boolean integral) {
}

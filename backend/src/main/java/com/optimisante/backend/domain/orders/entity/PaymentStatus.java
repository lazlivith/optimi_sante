package com.optimisante.backend.domain.orders.entity;

public enum PaymentStatus {
    UNPAID,
    PAID,
    PENDING_APPROVAL,
    QUOTE_SENT,
    QUOTE_REJECTED,
    /**
     * Remboursement intégral constaté chez Stripe.
     *
     * <p>Une commande qui porte ce statut sort des agrégats financiers, qui ne comptent que les
     * lignes au statut {@code PAID} : le chiffre d'affaires cesse donc d'annoncer un revenu pour
     * de l'argent rendu. Un remboursement <i>partiel</i> ne porte pas ce statut — voir
     * {@code V64__statut_rembourse.sql}.</p>
     */
    REFUNDED
}

package com.optimisante.backend.domain.orders.webhook;

import com.optimisante.backend.domain.finance.Devise;
import com.optimisante.backend.domain.finance.Montant;
import com.stripe.model.Charge;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Traite un remboursement émis depuis Stripe.
 *
 * <p>Jusqu'ici, rembourser un client depuis le tableau de bord Stripe ne produisait aucun effet
 * sur la plateforme : la commande restait marquée payée, et son montant continuait de figurer au
 * chiffre d'affaires. Un revenu annoncé pour de l'argent rendu.</p>
 *
 * <p><b>Pourquoi un composant distinct du {@link StripePaymentDispatcher}.</b> Celui-ci aiguille
 * des {@code Session} de paiement selon leur usage métier ; un remboursement porte un
 * {@code Charge}, et il n'a pas d'usage à résoudre — il se rattache à une commande par
 * l'identifiant de paiement. Les faire passer par la même abstraction aurait demandé de la
 * généraliser pour un seul cas.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StripeRefundHandler {

    private final DispatcherRemboursement dispatcherRemboursement;

    /**
     * Applique le remboursement à la commande correspondante.
     *
     * <p>L'événement {@code charge.refunded} est émis aussi bien pour un remboursement total que
     * partiel : c'est {@code refunded} qui tranche, et le montant rendu est reconverti depuis la
     * plus petite unité de la devise — Stripe ne transmet jamais autre chose.</p>
     */
    public void handle(Charge charge) {
        Devise devise = Devise.de(charge.getCurrency());
        Montant rendu = Montant.depuisPlusPetiteUnite(
                charge.getAmountRefunded() == null ? 0L : charge.getAmountRefunded(), devise);
        boolean integral = Boolean.TRUE.equals(charge.getRefunded());

        log.info("Remboursement Stripe reçu : paiement {}, {}, intégral = {}",
                charge.getPaymentIntent(), rendu, integral);

        dispatcherRemboursement.dispatch(
                new RemboursementConstate(charge.getPaymentIntent(), rendu, integral));
    }
}

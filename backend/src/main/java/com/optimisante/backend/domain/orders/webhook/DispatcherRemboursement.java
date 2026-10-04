package com.optimisante.backend.domain.orders.webhook;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Aiguille un remboursement vers le parcours qui reconnait le paiement.
 *
 * <p>Meme principe que {@code StripePaymentDispatcher} pour les confirmations : le point
 * d'entree du webhook ignore les parcours metier, et en ajouter un consiste a declarer un
 * {@link TraitementRemboursement}.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DispatcherRemboursement {

    private final List<TraitementRemboursement> traitements;

    /**
     * Confie le remboursement au premier parcours qui le reconnait.
     *
     * <p>Aucun ne le reconnait : ce n'est ni une erreur ni un silence. Le paiement peut venir
     * d'un parcours que la plateforme ne traite pas encore, ou d'un encaissement anterieur a
     * l'enregistrement des identifiants. L'avertissement nomme le paiement pour qu'il soit
     * repris a la main.</p>
     */
    public void dispatch(RemboursementConstate remboursement) {
        for (TraitementRemboursement traitement : traitements) {
            if (traitement.appliquer(remboursement)) {
                return;
            }
        }
        log.warn("Remboursement Stripe {} de {} : aucun parcours ne reconnait ce paiement. "
                + "A reprendre manuellement.",
                remboursement.paymentIntentId(), remboursement.montantRembourse());
    }
}

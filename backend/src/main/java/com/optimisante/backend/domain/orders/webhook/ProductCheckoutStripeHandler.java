package com.optimisante.backend.domain.orders.webhook;

import com.optimisante.backend.domain.orders.service.OrderService;
import com.stripe.model.checkout.Session;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Commande e-commerce B2C/B2B : usage historique et par défaut de Stripe Checkout sur la
 * plateforme. Ces sessions ne portent aucune métadonnée d'usage — c'est pourquoi une valeur
 * absente doit continuer d'aboutir ici, et nulle part ailleurs.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductCheckoutStripeHandler implements StripePaymentHandler {

    public static final String PAYMENT_TYPE = "PRODUCT_CHECKOUT";

    private final OrderService orderService;

    @Override
    public boolean supports(String paymentType) {
        return PAYMENT_TYPE.equals(paymentType);
    }

    @Override
    public void handle(UUID referenceId, Session session) {
        log.info("Paiement confirmé pour la commande {}", referenceId);
        orderService.confirmOrderPayment(referenceId);
    }
}

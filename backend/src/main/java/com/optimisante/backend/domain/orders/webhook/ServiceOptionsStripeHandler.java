package com.optimisante.backend.domain.orders.webhook;

import com.optimisante.backend.domain.training.finance.ServiceOptionsPaymentService;
import com.stripe.model.checkout.Session;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Services organises par OptimiSante autour du sejour, regles par le medecin.
 *
 * <p>Contrairement aux frais de formation, cet encaissement <b>ne fait pas avancer la
 * candidature</b> : il solde les services souscrits et depose l'attestation au coffre, sans
 * toucher au statut du dossier.</p>
 *
 * <p>Le {@code client_reference_id} porte ici l'identifiant du dossier ; la ligne exacte a
 * solder est nommee par une metadonnee de la session, plusieurs paiements de services pouvant
 * coexister sur un meme dossier.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ServiceOptionsStripeHandler implements StripePaymentHandler {

    public static final String PAYMENT_TYPE = "SERVICE_OPTIONS";

    private final ServiceOptionsPaymentService serviceOptionsPaymentService;

    @Override
    public boolean supports(String paymentType) {
        return PAYMENT_TYPE.equals(paymentType);
    }

    @Override
    public void handle(UUID enrollmentId, Session session) {
        log.info("Paiement de services confirmé pour le dossier {}", enrollmentId);
        serviceOptionsPaymentService.confirmServicePayment(enrollmentId, session);
    }
}

package com.optimisante.backend.domain.orders.webhook;

import com.optimisante.backend.domain.training.service.EnrollmentService;
import com.stripe.model.checkout.Session;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Frais de formation réglés par le médecin. C'est cet encaissement qui confirme
 * définitivement l'inscription et déclenche l'émission des documents.
 *
 * <p>Le {@code client_reference_id} porte ici l'identifiant du dossier
 * ({@code enrollment_id}), et non celui d'une commande.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TuitionPaymentStripeHandler implements StripePaymentHandler {

    public static final String PAYMENT_TYPE = "TUITION_FEE";

    private final EnrollmentService enrollmentService;

    @Override
    public boolean supports(String paymentType) {
        return PAYMENT_TYPE.equals(paymentType);
    }

    @Override
    public void handle(UUID enrollmentId, Session session) {
        log.info("Frais de formation confirmés pour le dossier {}", enrollmentId);
        enrollmentService.confirmTuitionPayment(
                enrollmentId, session.getId(), session.getPaymentIntent());
    }
}

package com.optimisante.backend.domain.orders.webhook;

import com.optimisante.backend.domain.doctorapplication.service.DoctorApplicationService;
import com.stripe.model.checkout.Session;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;

/**
 * Frais de dossier d'une candidature médecin : crée le compte, le profil et l'inscription
 * une fois le paiement confirmé.
 *
 * <p>Reconnaît deux valeurs : {@code DOCTOR_APPLICATION}, posée par le code en production
 * depuis la Phase 3, et {@code APPLICATION_FEE}, nomenclature retenue pour la suite.
 * Les deux doivent cohabiter le temps que les sessions en cours se soldent.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DoctorApplicationStripeHandler implements StripePaymentHandler {

    private static final Set<String> PAYMENT_TYPES = Set.of("DOCTOR_APPLICATION", "APPLICATION_FEE");

    private final DoctorApplicationService doctorApplicationService;

    @Override
    public boolean supports(String paymentType) {
        return PAYMENT_TYPES.contains(paymentType);
    }

    @Override
    public void handle(UUID referenceId, Session session) {
        log.info("Frais de dossier confirmés pour la candidature {}", referenceId);
        doctorApplicationService.confirmPayment(referenceId, session.getCustomer());
    }
}

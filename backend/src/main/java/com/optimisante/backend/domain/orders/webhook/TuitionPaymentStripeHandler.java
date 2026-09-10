package com.optimisante.backend.domain.orders.webhook;

import com.optimisante.backend.domain.training.finance.PaymentInstallment;
import com.optimisante.backend.domain.training.finance.TuitionPaymentService;
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

    /**
     * Aiguille selon le rang de l'echeance, porte par les metadonnees de la session.
     *
     * <p>Une session sans cette metadonnee est un <b>reglement unique</b> ({@code FULL}), et non
     * un acompte. Ce sont les sessions ouvertes avant la V45, encore en cours de paiement au
     * moment du deploiement : le candidat y a ete debite du prix plein. Les inscrire comme un
     * acompte ferait figurer 60 % au registre la ou 100 % ont ete preleves, et la plateforme
     * lui reclamerait ensuite un solde qu'il a deja paye.</p>
     */
    @Override
    public void handle(UUID enrollmentId, Session session) {
        String rang = session.getMetadata() == null
                ? null
                : session.getMetadata().get(TuitionPaymentService.METADATA_INSTALLMENT);

        if (PaymentInstallment.BALANCE.name().equals(rang)) {
            log.info("Solde de formation confirmé pour le dossier {}", enrollmentId);
            enrollmentService.confirmTuitionBalance(
                    enrollmentId, session.getId(), session.getPaymentIntent());
            return;
        }

        PaymentInstallment reserve = PaymentInstallment.DEPOSIT.name().equals(rang)
                ? PaymentInstallment.DEPOSIT
                : PaymentInstallment.FULL;
        log.info("Échéance {} de formation confirmée pour le dossier {}", reserve, enrollmentId);
        enrollmentService.confirmTuitionPayment(
                enrollmentId, session.getId(), session.getPaymentIntent(), reserve);
    }
}

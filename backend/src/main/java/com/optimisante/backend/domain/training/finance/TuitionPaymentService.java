package com.optimisante.backend.domain.training.finance;

import com.optimisante.backend.domain.orders.service.StripePaymentService;
import com.optimisante.backend.domain.training.entity.Enrollment;
import com.optimisante.backend.domain.training.entity.EnrollmentStatus;
import com.optimisante.backend.domain.training.repository.EnrollmentRepository;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Ouverture du paiement des frais de formation par le médecin.
 *
 * <p>Réutilise {@link StripePaymentService} tel quel : le tunnel e-commerce n'est pas
 * touché, seule la métadonnée {@code payment_type} distingue cet usage à la réception
 * du webhook.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TuitionPaymentService {

    private final EnrollmentRepository enrollmentRepository;
    private final EnrollmentPaymentService enrollmentPaymentService;
    private final StripePaymentService stripePaymentService;

    @Value("${app.mail.frontend-base-url}")
    private String frontendBaseUrl;

    /**
     * Prépare la session de paiement et la ligne du registre associée.
     *
     * @return le {@code clientSecret} du formulaire de paiement intégré
     */
    @Transactional
    public TuitionCheckoutDto createTuitionCheckoutSession(UUID enrollmentId, UUID doctorId) {
        Enrollment enrollment = enrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new IllegalArgumentException("Dossier introuvable"));

        if (!enrollment.getDoctor().getId().equals(doctorId)) {
            throw new AccessDeniedException("Ce dossier ne vous appartient pas.");
        }
        if (enrollment.getStatus() != EnrollmentStatus.PENDING_TUITION_FEE) {
            throw new IllegalStateException(
                    "Le paiement de la formation n'est ouvert qu'une fois la candidature acceptée "
                            + "par l'établissement (statut actuel : " + enrollment.getStatus() + ").");
        }

        // Ouvre (ou retrouve) la ligne en attente, avec sa répartition déjà figée.
        EnrollmentPayment payment = enrollmentPaymentService.openTuitionPayment(enrollmentId);

        try {
            Session session = stripePaymentService.createElementsCheckoutSessionForNewCustomer(
                    enrollmentId,
                    payment.getGrossAmount(),
                    enrollment.getDoctor().getEmail(),
                    frontendBaseUrl + "/doctor/enrollments/" + enrollmentId,
                    "Frais de formation — " + enrollment.getSession().getTraining().getTitle(),
                    Map.of(
                            "payment_type", "TUITION_FEE",
                            "enrollment_id", enrollmentId.toString()));

            payment.setStripeCheckoutSessionId(session.getId());

            log.info("Session de paiement de formation ouverte pour le dossier {} : {} EUR",
                    enrollmentId, payment.getGrossAmount());

            return new TuitionCheckoutDto(
                    session.getClientSecret(), payment.getGrossAmount(), payment.getCurrency());

        } catch (StripeException e) {
            log.error("Échec de création de la session Stripe pour le dossier {}", enrollmentId, e);
            // Distinguer une donnée invalide d'une panne : un tarif mal configuré (montant
            // sous le minimum Stripe, devise non supportée...) se corrige côté plateforme,
            // alors qu'un message « service indisponible » enverrait l'administrateur
            // chercher une panne inexistante.
            if (e instanceof com.stripe.exception.InvalidRequestException) {
                throw new IllegalStateException(
                        "Le paiement n'a pas pu être ouvert : " + e.getStripeError().getMessage()
                                + " Vérifiez le tarif de la session de formation.");
            }
            throw new IllegalStateException("Le service de paiement est momentanément indisponible.");
        }
    }

    public record TuitionCheckoutDto(String clientSecret, java.math.BigDecimal amount, String currency) {
    }
}

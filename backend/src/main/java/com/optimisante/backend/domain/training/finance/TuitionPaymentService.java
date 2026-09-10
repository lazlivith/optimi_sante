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
 * Reglement des frais de formation, en deux echeances.
 *
 * <p><b>Acompte a l'admission, solde a la delivrance du visa.</b> Le candidat n'avance plus la
 * totalite d'un sejour qui depend encore d'une decision consulaire ; l'etablissement, lui, est
 * paye a chaque echeance — les deux lignes alimentent le meme reversement.</p>
 *
 * <p>Reutilise {@link StripePaymentService} tel quel : le tunnel e-commerce n'est pas touche,
 * seules les metadonnees de la session distinguent cet usage et le rang de l'echeance a la
 * reception du webhook.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TuitionPaymentService {

    /** Portee par la session : elle dit au webhook laquelle des deux echeances est reglee. */
    public static final String METADATA_INSTALLMENT = "tuition_installment";

    private final EnrollmentRepository enrollmentRepository;
    private final EnrollmentPaymentService enrollmentPaymentService;
    private final StripePaymentService stripePaymentService;

    @Value("${app.mail.frontend-base-url}")
    private String frontendBaseUrl;

    /**
     * Ouvre l'acompte : possible une fois la candidature acceptee par l'etablissement.
     *
     * @return le {@code clientSecret} du formulaire de paiement integre
     */
    @Transactional
    public TuitionCheckoutDto createTuitionCheckoutSession(UUID enrollmentId, UUID doctorId) {
        Enrollment enrollment = requireOwned(enrollmentId, doctorId);

        if (enrollment.getStatus() != EnrollmentStatus.PENDING_TUITION_FEE) {
            throw new IllegalStateException(
                    "Le paiement de la formation n'est ouvert qu'une fois la candidature acceptée "
                            + "par l'établissement (statut actuel : " + enrollment.getStatus() + ").");
        }

        EnrollmentPayment payment = enrollmentPaymentService.openTuitionDeposit(enrollmentId);
        return ouvrirSession(enrollment, payment, PaymentInstallment.DEPOSIT,
                "Acompte de formation — ");
    }

    /**
     * Ouvre le solde : possible a partir de la delivrance du visa.
     *
     * <p>Pas avant : reclamer le solde tant que le visa n'est pas accorde ferait payer au
     * candidat un sejour qui peut encore ne pas avoir lieu — c'est precisement ce que
     * l'echeancier evite.</p>
     */
    @Transactional
    public TuitionCheckoutDto createBalanceCheckoutSession(UUID enrollmentId, UUID doctorId) {
        Enrollment enrollment = requireOwned(enrollmentId, doctorId);

        if (!SOLDE_APPELABLE.contains(enrollment.getStatus())) {
            throw new IllegalStateException(
                    "Le solde n'est appelé qu'à la délivrance du visa (statut actuel : "
                            + enrollment.getStatus() + ").");
        }

        EnrollmentPayment payment = enrollmentPaymentService.openTuitionBalance(enrollmentId);
        return ouvrirSession(enrollment, payment, PaymentInstallment.BALANCE,
                "Solde de formation — ");
    }

    /**
     * Etats depuis lesquels le solde peut etre regle.
     *
     * <p>{@code READY_TO_START} y figure bien qu'il suppose le solde paye : un dossier peut y
     * etre arrive avant la mise en place de l'echeancier, et lui fermer le reglement le
     * laisserait avec une dette impossible a honorer.</p>
     */
    private static final java.util.EnumSet<EnrollmentStatus> SOLDE_APPELABLE =
            java.util.EnumSet.of(EnrollmentStatus.VISA_GRANTED, EnrollmentStatus.READY_TO_START);

    private TuitionCheckoutDto ouvrirSession(Enrollment enrollment, EnrollmentPayment payment,
                                             PaymentInstallment rang, String prefixe) {
        UUID enrollmentId = enrollment.getId();
        try {
            Session session = stripePaymentService.createElementsCheckoutSessionForNewCustomer(
                    enrollmentId,
                    payment.getGrossAmount(),
                    enrollment.getDoctor().getEmail(),
                    frontendBaseUrl + "/doctor/enrollments/" + enrollmentId,
                    prefixe + enrollment.getSession().getTraining().getTitle(),
                    Map.of(
                            "payment_type", "TUITION_FEE",
                            "enrollment_id", enrollmentId.toString(),
                            METADATA_INSTALLMENT, rang.name()));

            payment.setStripeCheckoutSessionId(session.getId());

            log.info("Session de paiement {} ouverte pour le dossier {} : {} EUR",
                    rang, enrollmentId, payment.getGrossAmount());

            return new TuitionCheckoutDto(
                    session.getClientSecret(), payment.getGrossAmount(), payment.getCurrency());

        } catch (StripeException e) {
            log.error("Échec de création de la session Stripe ({}) pour le dossier {}",
                    rang, enrollmentId, e);
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

    private Enrollment requireOwned(UUID enrollmentId, UUID doctorId) {
        Enrollment enrollment = enrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new IllegalArgumentException("Dossier introuvable"));
        if (!enrollment.getDoctor().getId().equals(doctorId)) {
            throw new AccessDeniedException("Ce dossier ne vous appartient pas.");
        }
        return enrollment;
    }

    public record TuitionCheckoutDto(String clientSecret, java.math.BigDecimal amount, String currency) {
    }
}

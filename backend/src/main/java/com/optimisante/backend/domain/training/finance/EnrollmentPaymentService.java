package com.optimisante.backend.domain.training.finance;

import com.optimisante.backend.domain.training.entity.Enrollment;
import com.optimisante.backend.domain.training.repository.EnrollmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Tenue du registre financier des candidatures.
 *
 * <p>Seul endroit du code qui écrit dans {@code enrollment_payments}. La répartition est
 * systématiquement produite par {@link FinancialSplitCalculator}, jamais calculée à la main
 * sur place : c'est ce qui garantit que l'invariant vérifié par PostgreSQL
 * ({@code commission + part partenaire = brut}) est toujours satisfait.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EnrollmentPaymentService {

    /** Frais de dossier et services organisés par l'agence reviennent intégralement à la plateforme. */
    private static final BigDecimal FULL_PLATFORM_RATE = new BigDecimal("100.00");

    private final EnrollmentPaymentRepository paymentRepository;
    private final EnrollmentRepository enrollmentRepository;

    @Transactional(readOnly = true)
    public List<EnrollmentPayment> getPayments(UUID enrollmentId) {
        return paymentRepository.findByEnrollmentIdOrderByCreatedAtDesc(enrollmentId);
    }

    /**
     * Reflète dans le registre des frais de dossier déjà encaissés ailleurs
     * (via {@code doctor_applications}, porte d'entrée inchangée).
     *
     * <p>Idempotent : un reflet déjà présent n'est pas dupliqué. L'index unique partiel
     * {@code uq_enrollment_paid_dossier} sert de second rempart côté base.</p>
     */
    @Transactional
    public Optional<EnrollmentPayment> reflectDossierFee(UUID enrollmentId, BigDecimal feeAmount,
                                                         OffsetDateTime paidAt) {
        if (feeAmount == null || feeAmount.signum() <= 0) {
            return Optional.empty();
        }
        Optional<EnrollmentPayment> existing = paymentRepository
                .findByEnrollmentIdAndPaymentTypeAndStatus(
                        enrollmentId, PaymentType.DOSSIER_FEE, PaymentStatus.PAID);
        if (existing.isPresent()) {
            return existing;
        }

        Enrollment enrollment = requireEnrollment(enrollmentId);
        var split = FinancialSplitCalculator.calculateSplit(feeAmount, FULL_PLATFORM_RATE);

        EnrollmentPayment payment = paymentRepository.save(EnrollmentPayment.builder()
                .enrollment(enrollment)
                .paymentType(PaymentType.DOSSIER_FEE)
                .grossAmount(split.grossAmount())
                .commissionRate(split.commissionRate())
                .commissionAmount(split.commissionAmount())
                .partnerPayoutAmount(split.partnerPayoutAmount())
                .status(PaymentStatus.PAID)
                .paidAt(paidAt != null ? paidAt : OffsetDateTime.now())
                .build());

        log.info("Frais de dossier reflétés au registre pour le dossier {} : {} EUR",
                enrollmentId, split.grossAmount());
        return Optional.of(payment);
    }

    /**
     * Ouvre une ligne de paiement de formation en attente, avec sa répartition déjà calculée
     * au taux en vigueur pour ce partenaire — figé dès maintenant sur la ligne.
     *
     * <p>Le montant vient de la session ; à défaut, du tarif de la formation. Une session
     * peut en effet porter un prix propre (tarif négocié pour une promotion donnée).</p>
     */
    @Transactional
    public EnrollmentPayment openTuitionPayment(UUID enrollmentId) {
        Optional<EnrollmentPayment> alreadyPaid = paymentRepository
                .findByEnrollmentIdAndPaymentTypeAndStatus(
                        enrollmentId, PaymentType.TUITION_FEE, PaymentStatus.PAID);
        if (alreadyPaid.isPresent()) {
            throw new IllegalStateException("La formation de ce dossier est déjà réglée.");
        }

        Enrollment enrollment = requireEnrollment(enrollmentId);
        BigDecimal gross = resolveTuitionAmount(enrollment);
        BigDecimal rate = resolveCommissionRate(enrollment);

        var split = FinancialSplitCalculator.calculateSplit(gross, rate);

        EnrollmentPayment payment = paymentRepository.save(EnrollmentPayment.builder()
                .enrollment(enrollment)
                .paymentType(PaymentType.TUITION_FEE)
                .grossAmount(split.grossAmount())
                .commissionRate(split.commissionRate())
                .commissionAmount(split.commissionAmount())
                .partnerPayoutAmount(split.partnerPayoutAmount())
                .status(PaymentStatus.PENDING)
                .build());

        log.info("Paiement de formation ouvert pour le dossier {} : {} EUR "
                        + "(commission {} EUR, part partenaire {} EUR)",
                enrollmentId, split.grossAmount(), split.commissionAmount(), split.partnerPayoutAmount());
        return payment;
    }

    /**
     * Ouvre une ligne d'encaissement pour les services souscrits, entièrement acquise à la
     * plateforme.
     *
     * <p>Aucun contrôle « déjà payé » ici, contrairement aux frais de formation : plusieurs
     * paiements de services sur un même dossier sont légitimes. C'est l'appelant qui décide
     * quels services entrent dans ce panier, et la session Stripe qui porte l'idempotence.</p>
     */
    @Transactional
    public EnrollmentPayment openServiceOptionsPayment(UUID enrollmentId, BigDecimal gross) {
        if (gross == null || gross.signum() <= 0) {
            throw new IllegalStateException("Aucun service à régler sur ce dossier.");
        }
        Enrollment enrollment = requireEnrollment(enrollmentId);
        var split = FinancialSplitCalculator.calculateSplit(gross, FULL_PLATFORM_RATE);

        EnrollmentPayment payment = paymentRepository.save(EnrollmentPayment.builder()
                .enrollment(enrollment)
                .paymentType(PaymentType.SERVICE_OPTIONS)
                .grossAmount(split.grossAmount())
                .commissionRate(split.commissionRate())
                .commissionAmount(split.commissionAmount())
                .partnerPayoutAmount(split.partnerPayoutAmount())
                .status(PaymentStatus.PENDING)
                .build());

        log.info("Paiement de services ouvert pour le dossier {} : {} EUR, 100 % plateforme",
                enrollmentId, split.grossAmount());
        return payment;
    }

    /** Montant à facturer : prix de la session, sinon prix de la formation. */
    public BigDecimal resolveTuitionAmount(Enrollment enrollment) {
        BigDecimal sessionPrice = enrollment.getSession().getPrice();
        if (sessionPrice != null && sessionPrice.signum() > 0) {
            return sessionPrice;
        }
        BigDecimal trainingPrice = enrollment.getSession().getTraining().getPrice();
        if (trainingPrice == null || trainingPrice.signum() <= 0) {
            throw new IllegalStateException(
                    "Aucun tarif défini pour cette formation : le paiement ne peut pas être ouvert.");
        }
        return trainingPrice;
    }

    /** Taux négocié du partenaire propriétaire de la formation. */
    public BigDecimal resolveCommissionRate(Enrollment enrollment) {
        BigDecimal rate = enrollment.getSession().getTraining().getPartnerProfile().getCommissionRate();
        return rate != null ? rate : BigDecimal.ZERO;
    }

    private Enrollment requireEnrollment(UUID enrollmentId) {
        return enrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new IllegalArgumentException("Dossier introuvable"));
    }
}

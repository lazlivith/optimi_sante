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

    /**
     * Part exigee a l'admission. Le solde suit a la delivrance du visa.
     *
     * <p>Lue en configuration, jamais ecrite en dur : le contrat opposable (CGV) annonce ce
     * taux, et un chiffre fige dans le code se serait desynchronise du contrat au premier
     * changement — l'incident survenu sur les frais de dossier.</p>
     */
    @org.springframework.beans.factory.annotation.Value("${app.tuition.deposit-rate}")
    private BigDecimal depositRate;

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
                // Pas de rang : les frais de dossier ne s'echelonnent pas (contrainte V45).
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
     * Ouvre l'acompte de formation, exigible une fois la candidature acceptee.
     *
     * <p>La repartition avec le partenaire est calculee au taux en vigueur et <b>figee des
     * maintenant</b> sur la ligne, comme depuis la V27 : renegocier le taux d'un partenaire ne
     * doit pas reecrire un historique deja constate.</p>
     */
    @Transactional
    public EnrollmentPayment openTuitionDeposit(UUID enrollmentId) {
        if (findPaidTuition(enrollmentId, PaymentInstallment.DEPOSIT).isPresent()
                || findPaidTuition(enrollmentId, PaymentInstallment.FULL).isPresent()) {
            throw new IllegalStateException("L'acompte de ce dossier est déjà réglé.");
        }

        Enrollment enrollment = requireEnrollment(enrollmentId);
        var echeancier = TuitionInstallmentCalculator.split(
                resolveTuitionAmount(enrollment), depositRate);

        return ouvrirEcheance(enrollment, PaymentInstallment.DEPOSIT,
                echeancier.depositAmount(), resolveCommissionRate(enrollment));
    }

    /**
     * Ouvre le solde de formation, exigible a la delivrance du visa.
     *
     * <p><b>Le taux de commission est repris de l'acompte</b>, et non relu sur le profil du
     * partenaire. Les deux echeances reglent une meme inscription, conclue a des conditions
     * donnees : une renegociation intervenue entre-temps s'appliquerait sinon a la moitie du
     * prix d'un contrat deja forme.</p>
     */
    @Transactional
    public EnrollmentPayment openTuitionBalance(UUID enrollmentId) {
        if (findPaidTuition(enrollmentId, PaymentInstallment.FULL).isPresent()) {
            throw new IllegalStateException(
                    "Ce dossier a été réglé en une fois : aucun solde n'est dû.");
        }
        EnrollmentPayment acompte = findPaidTuition(enrollmentId, PaymentInstallment.DEPOSIT)
                .orElseThrow(() -> new IllegalStateException(
                        "L'acompte doit être réglé avant que le solde puisse être appelé."));
        if (findPaidTuition(enrollmentId, PaymentInstallment.BALANCE).isPresent()) {
            throw new IllegalStateException("Le solde de ce dossier est déjà réglé.");
        }

        Enrollment enrollment = requireEnrollment(enrollmentId);
        var echeancier = TuitionInstallmentCalculator.split(
                resolveTuitionAmount(enrollment), depositRate);
        if (!echeancier.hasBalance()) {
            throw new IllegalStateException("Aucun solde n'est dû sur ce dossier.");
        }

        return ouvrirEcheance(enrollment, PaymentInstallment.BALANCE,
                echeancier.balanceAmount(), acompte.getCommissionRate());
    }

    /** Ce qui reste du au titre de la formation, zero si tout est regle. */
    @Transactional(readOnly = true)
    public BigDecimal outstandingTuition(UUID enrollmentId) {
        if (findPaidTuition(enrollmentId, PaymentInstallment.FULL).isPresent()) {
            return BigDecimal.ZERO;
        }
        Enrollment enrollment = requireEnrollment(enrollmentId);
        var echeancier = TuitionInstallmentCalculator.split(
                resolveTuitionAmount(enrollment), depositRate);

        BigDecimal du = BigDecimal.ZERO;
        if (findPaidTuition(enrollmentId, PaymentInstallment.DEPOSIT).isEmpty()) {
            du = du.add(echeancier.depositAmount());
        }
        if (findPaidTuition(enrollmentId, PaymentInstallment.BALANCE).isEmpty()) {
            du = du.add(echeancier.balanceAmount());
        }
        return du;
    }

    /** L'echeancier tel qu'il s'applique a ce dossier, pour l'affichage. */
    @Transactional(readOnly = true)
    public TuitionInstallmentCalculator.TuitionSchedule scheduleFor(Enrollment enrollment) {
        return TuitionInstallmentCalculator.split(resolveTuitionAmount(enrollment), depositRate);
    }

    public Optional<EnrollmentPayment> findPaidTuition(UUID enrollmentId,
                                                       PaymentInstallment installment) {
        return paymentRepository.findByEnrollmentIdAndPaymentTypeAndInstallmentAndStatus(
                enrollmentId, PaymentType.TUITION_FEE, installment, PaymentStatus.PAID);
    }

    /** Ligne en attente pour une echeance donnee, s'il en existe une. */
    public Optional<EnrollmentPayment> findPendingTuition(UUID enrollmentId,
                                                          PaymentInstallment installment) {
        return paymentRepository.findByEnrollmentIdAndPaymentTypeAndInstallmentAndStatus(
                enrollmentId, PaymentType.TUITION_FEE, installment, PaymentStatus.PENDING);
    }

    private EnrollmentPayment ouvrirEcheance(Enrollment enrollment, PaymentInstallment rang,
                                             BigDecimal gross, BigDecimal rate) {
        var split = FinancialSplitCalculator.calculateSplit(gross, rate);

        EnrollmentPayment payment = paymentRepository.save(EnrollmentPayment.builder()
                .enrollment(enrollment)
                .paymentType(PaymentType.TUITION_FEE)
                .installment(rang)
                .grossAmount(split.grossAmount())
                .commissionRate(split.commissionRate())
                .commissionAmount(split.commissionAmount())
                .partnerPayoutAmount(split.partnerPayoutAmount())
                .status(PaymentStatus.PENDING)
                .build());

        log.info("Échéance {} ouverte pour le dossier {} : {} EUR "
                        + "(commission {} EUR, part partenaire {} EUR)",
                rang, enrollment.getId(), split.grossAmount(),
                split.commissionAmount(), split.partnerPayoutAmount());
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
                // Pas de rang : les services ne s'echelonnent pas (contrainte V45).
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

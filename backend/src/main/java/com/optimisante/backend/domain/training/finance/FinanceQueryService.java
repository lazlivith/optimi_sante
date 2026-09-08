package com.optimisante.backend.domain.training.finance;

import com.optimisante.backend.domain.identity.entity.PartnerProfile;
import com.optimisante.backend.domain.identity.repository.DoctorProfileRepository;
import com.optimisante.backend.domain.identity.repository.PartnerProfileRepository;
import com.optimisante.backend.domain.training.entity.Enrollment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Lectures du registre financier, pour les écrans d'administration et de partenaire.
 *
 * <p><b>Cloisonnement.</b> Les vues admin et partenaire n'exposent délibérément pas les mêmes
 * champs, et cette différence est portée par deux types distincts plutôt que par un filtrage
 * à l'affichage : la vue partenaire ne peut pas transporter la commission, même par erreur,
 * même dans une réponse JSON que personne ne regarde.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FinanceQueryService {

    private final EnrollmentPaymentRepository paymentRepository;
    private final PartnerProfileRepository partnerProfileRepository;
    private final DoctorProfileRepository doctorProfileRepository;
    private final PartnerPayoutService payoutService;

    // ================================================================= ADMIN ====

    /** Registre complet : toutes les transactions, avec leur répartition. */
    @Transactional(readOnly = true)
    public List<AdminPaymentRow> getAllPayments() {
        return paymentRepository.findAll().stream()
                .sorted(Comparator.comparing(EnrollmentPayment::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(this::toAdminRow)
                .toList();
    }

    /** Indicateurs consolidés de la plateforme. */
    @Transactional(readOnly = true)
    public AdminFinanceKpis getKpis() {
        List<EnrollmentPayment> paid = paymentRepository.findAll().stream()
                .filter(p -> p.getStatus() == PaymentStatus.PAID)
                .toList();

        BigDecimal collected = sum(paid, EnrollmentPayment::getGrossAmount);
        BigDecimal commission = sum(paid, EnrollmentPayment::getCommissionAmount);

        BigDecimal awaitingPayout = sum(
                paid.stream().filter(p -> p.getPartnerPayout() == null).toList(),
                EnrollmentPayment::getPartnerPayoutAmount);

        BigDecimal paidOut = partnerProfileRepository.findAll().stream()
                .map(pp -> payoutService.getSummary(pp.getId()).paidOut())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new AdminFinanceKpis(collected, commission, awaitingPayout, paidOut, paid.size());
    }

    /** Une ligne par partenaire : ce qui lui est dû et ce qui lui a déjà été versé. */
    @Transactional(readOnly = true)
    public List<PartnerDueRow> getPartnersDue() {
        return partnerProfileRepository.findAll().stream()
                .map(pp -> {
                    var summary = payoutService.getSummary(pp.getId());
                    return new PartnerDueRow(
                            pp.getId(), pp.getInstitutionName(),
                            summary.pendingAmount(), summary.awaitingTransfer(),
                            summary.paidOut(), summary.pendingPaymentsCount());
                })
                .sorted(Comparator.comparing(PartnerDueRow::pendingAmount).reversed())
                .toList();
    }

    // ============================================================ PARTENAIRE ====

    /**
     * Inscriptions réglées d'un partenaire.
     *
     * <p>Ne renvoie que la part nette qui lui revient : ni le montant brut, ni la commission,
     * ni le taux. Exposer le brut à côté du net reviendrait à publier la commission par
     * simple soustraction — la restriction serait cosmétique.</p>
     */
    @Transactional(readOnly = true)
    public List<PartnerPaymentRow> getPartnerPayments(UUID partnerProfileId) {
        return paymentRepository.findAll().stream()
                .filter(p -> p.getPaymentType() == PaymentType.TUITION_FEE)
                .filter(p -> p.getStatus() == PaymentStatus.PAID)
                .filter(p -> belongsToPartner(p, partnerProfileId))
                .sorted(Comparator.comparing(EnrollmentPayment::getPaidAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(p -> new PartnerPaymentRow(
                        p.getId(),
                        doctorName(p.getEnrollment()),
                        p.getEnrollment().getSession().getTraining().getTitle(),
                        p.getEnrollment().getSession().getStartDate(),
                        p.getPaidAt(),
                        p.getPartnerPayoutAmount(),
                        p.getCurrency(),
                        p.getPartnerPayout() != null ? p.getPartnerPayout().getReference() : null))
                .toList();
    }

    // =============================================================================

    private boolean belongsToPartner(EnrollmentPayment payment, UUID partnerProfileId) {
        PartnerProfile owner = payment.getEnrollment().getSession().getTraining().getPartnerProfile();
        return owner != null && owner.getId().equals(partnerProfileId);
    }

    private AdminPaymentRow toAdminRow(EnrollmentPayment p) {
        Enrollment e = p.getEnrollment();
        PartnerProfile partner = e.getSession().getTraining().getPartnerProfile();
        return new AdminPaymentRow(
                p.getId(), e.getId(),
                doctorName(e), e.getDoctor().getEmail(),
                e.getSession().getTraining().getTitle(),
                partner != null ? partner.getInstitutionName() : null,
                p.getPaymentType().name(), p.getStatus().name(),
                p.getGrossAmount(), p.getCommissionRate(), p.getCommissionAmount(),
                p.getPartnerPayoutAmount(), p.getCurrency(),
                p.getPaidAt(), p.getCreatedAt(),
                p.getPartnerPayout() != null ? p.getPartnerPayout().getReference() : null);
    }

    private String doctorName(Enrollment enrollment) {
        return doctorProfileRepository.findByUserId(enrollment.getDoctor().getId())
                .map(d -> "Dr. " + d.getFirstName() + " " + d.getLastName())
                .orElse(enrollment.getDoctor().getEmail());
    }

    private BigDecimal sum(List<EnrollmentPayment> payments,
                           java.util.function.Function<EnrollmentPayment, BigDecimal> field) {
        return payments.stream().map(field).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ---- Vues ------------------------------------------------------------------------

    public record AdminPaymentRow(
            UUID id, UUID enrollmentId, String doctorName, String doctorEmail,
            String trainingTitle, String partnerName, String paymentType, String status,
            BigDecimal grossAmount, BigDecimal commissionRate, BigDecimal commissionAmount,
            BigDecimal partnerPayoutAmount, String currency,
            OffsetDateTime paidAt, OffsetDateTime createdAt, String payoutReference) {
    }

    public record AdminFinanceKpis(
            BigDecimal totalCollected, BigDecimal totalCommission,
            BigDecimal awaitingPayout, BigDecimal totalPaidOut, int paidTransactions) {
    }

    public record PartnerDueRow(
            UUID partnerProfileId, String institutionName,
            BigDecimal pendingAmount, BigDecimal awaitingTransfer,
            BigDecimal paidOut, int pendingPaymentsCount) {
    }

    /** Vue partenaire : volontairement dépourvue de tout champ de commission. */
    public record PartnerPaymentRow(
            UUID id, String doctorName, String trainingTitle,
            java.time.LocalDate sessionStartDate, OffsetDateTime paidAt,
            BigDecimal netAmount, String currency, String payoutReference) {
    }
}

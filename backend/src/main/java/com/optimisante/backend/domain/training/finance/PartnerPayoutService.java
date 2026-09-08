package com.optimisante.backend.domain.training.finance;

import com.optimisante.backend.domain.identity.entity.PartnerProfile;
import com.optimisante.backend.domain.identity.repository.PartnerProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * Génération des reversements dus aux partenaires.
 *
 * <p>Un reversement fige une dette : il agrège les parts nettes des encaissements réglés et
 * non encore reversés d'une période, puis les rattache définitivement à ce virement. Une
 * ligne déjà rattachée n'est jamais reprise — c'est ce qui garantit qu'un même encaissement
 * ne peut pas être payé deux fois au partenaire.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PartnerPayoutService {

    private final EnrollmentPaymentRepository paymentRepository;
    private final PartnerPayoutRepository payoutRepository;
    private final PartnerProfileRepository partnerProfileRepository;

    /**
     * Génère le reversement d'une période pour un partenaire.
     *
     * @param periodStart borne incluse, ou {@code null} pour ne pas borner le début
     * @param periodEnd   borne EXCLUE, ou {@code null} pour ne pas borner la fin
     * @throws IllegalStateException si aucun encaissement n'est reversable sur la période
     */
    @Transactional
    public PartnerPayout generatePayout(UUID partnerProfileId, LocalDate periodStart, LocalDate periodEnd) {
        PartnerProfile partner = partnerProfileRepository.findById(partnerProfileId)
                .orElseThrow(() -> new IllegalArgumentException("Partenaire introuvable"));

        if (periodStart != null && periodEnd != null && !periodEnd.isAfter(periodStart)) {
            throw new IllegalArgumentException("La fin de période doit être postérieure à son début.");
        }

        List<EnrollmentPayment> reversible = paymentRepository
                .findReversiblePayments(partnerProfileId).stream()
                .filter(p -> withinPeriod(p.getPaidAt(), periodStart, periodEnd))
                .toList();

        if (reversible.isEmpty()) {
            throw new IllegalStateException(
                    "Aucun encaissement à reverser pour ce partenaire sur la période demandée.");
        }

        BigDecimal total = reversible.stream()
                .map(EnrollmentPayment::getPartnerPayoutAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        PartnerPayout payout = payoutRepository.save(PartnerPayout.builder()
                .partnerProfile(partner)
                .totalAmount(total)
                .periodStart(periodStart)
                .periodEnd(periodEnd)
                .status(PayoutStatus.PENDING)
                .reference(buildReference(periodStart))
                .build());

        // Rattachement : ces encaissements sortent définitivement de l'assiette suivante.
        reversible.forEach(p -> p.setPartnerPayout(payout));
        paymentRepository.saveAll(reversible);

        log.info("Reversement {} généré pour le partenaire {} : {} EUR sur {} encaissement(s)",
                payout.getReference(), partnerProfileId, total, reversible.size());
        return payout;
    }

    /** Marque un reversement comme effectivement viré au partenaire. */
    @Transactional
    public PartnerPayout markAsPaid(UUID payoutId) {
        PartnerPayout payout = payoutRepository.findById(payoutId)
                .orElseThrow(() -> new IllegalArgumentException("Reversement introuvable"));

        if (payout.getStatus() != PayoutStatus.PENDING) {
            throw new IllegalStateException(
                    "Seul un reversement en attente peut être marqué comme payé (statut actuel : "
                            + payout.getStatus() + ").");
        }
        payout.setStatus(PayoutStatus.PAID);
        payout.setPaidAt(OffsetDateTime.now());

        log.info("Reversement {} marqué comme payé", payout.getReference());
        return payoutRepository.save(payout);
    }

    @Transactional(readOnly = true)
    public List<PartnerPayout> getPayouts(UUID partnerProfileId) {
        return payoutRepository.findByPartnerProfileIdOrderByCreatedAtDesc(partnerProfileId);
    }

    /** Synthèse financière d'un partenaire, telle qu'affichée dans son tableau de bord. */
    @Transactional(readOnly = true)
    public PartnerFinancialSummary getSummary(UUID partnerProfileId) {
        List<EnrollmentPayment> pending = paymentRepository.findReversiblePayments(partnerProfileId);

        BigDecimal pendingAmount = pending.stream()
                .map(EnrollmentPayment::getPartnerPayoutAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<PartnerPayout> payouts = payoutRepository
                .findByPartnerProfileIdOrderByCreatedAtDesc(partnerProfileId);

        BigDecimal paidOut = payouts.stream()
                .filter(p -> p.getStatus() == PayoutStatus.PAID)
                .map(PartnerPayout::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal awaitingTransfer = payouts.stream()
                .filter(p -> p.getStatus() == PayoutStatus.PENDING)
                .map(PartnerPayout::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new PartnerFinancialSummary(
                pendingAmount, awaitingTransfer, paidOut, pending.size(), payouts.size());
    }

    // ---------------------------------------------------------------------------------

    private boolean withinPeriod(OffsetDateTime paidAt, LocalDate start, LocalDate end) {
        if (paidAt == null) return false;
        LocalDate day = paidAt.atZoneSameInstant(ZoneOffset.UTC).toLocalDate();
        if (start != null && day.isBefore(start)) return false;
        return end == null || day.isBefore(end);
    }

    private String buildReference(LocalDate periodStart) {
        LocalDate base = periodStart != null ? periodStart : LocalDate.now();
        return "REV-%d-%02d-%s".formatted(
                base.getYear(), base.getMonthValue(),
                UUID.randomUUID().toString().substring(0, 6).toUpperCase());
    }

    /**
     * @param pendingAmount   parts nettes encaissées mais pas encore rattachées à un reversement
     * @param awaitingTransfer reversements générés, virement non encore effectué
     * @param paidOut         total déjà viré au partenaire
     */
    public record PartnerFinancialSummary(
            BigDecimal pendingAmount,
            BigDecimal awaitingTransfer,
            BigDecimal paidOut,
            int pendingPaymentsCount,
            int payoutsCount) {
    }
}

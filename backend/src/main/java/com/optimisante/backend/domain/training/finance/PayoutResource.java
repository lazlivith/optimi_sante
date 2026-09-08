package com.optimisante.backend.domain.training.finance;

import com.optimisante.backend.domain.identity.repository.PartnerProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Reversements partenaires : génération et suivi côté administration, consultation en
 * lecture seule côté partenaire.
 *
 * <p>Les deux espaces partagent la même représentation, mais jamais le même périmètre :
 * un partenaire ne peut consulter que ses propres reversements, résolus depuis son compte
 * et non depuis un identifiant fourni par l'appelant.</p>
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PayoutResource {

    private final PartnerPayoutService payoutService;
    private final PartnerProfileRepository partnerProfileRepository;
    private final FinanceQueryService financeQueryService;

    // ------------------------------------------------------------------ ADMIN ----

    @PostMapping("/admin/partners/{partnerProfileId}/payouts")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<PayoutDto> generatePayout(
            @PathVariable UUID partnerProfileId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodStart,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodEnd) {
        return ResponseEntity.ok(toDto(
                payoutService.generatePayout(partnerProfileId, periodStart, periodEnd)));
    }

    @PostMapping("/admin/payouts/{payoutId}/mark-paid")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<PayoutDto> markPaid(@PathVariable UUID payoutId) {
        return ResponseEntity.ok(toDto(payoutService.markAsPaid(payoutId)));
    }

    @GetMapping("/admin/partners/{partnerProfileId}/payouts")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<List<PayoutDto>> listPayoutsForAdmin(@PathVariable UUID partnerProfileId) {
        return ResponseEntity.ok(payoutService.getPayouts(partnerProfileId).stream().map(this::toDto).toList());
    }

    @GetMapping("/admin/partners/{partnerProfileId}/financial-summary")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<PartnerPayoutService.PartnerFinancialSummary> summaryForAdmin(
            @PathVariable UUID partnerProfileId) {
        return ResponseEntity.ok(payoutService.getSummary(partnerProfileId));
    }

    @GetMapping("/admin/payments")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<List<FinanceQueryService.AdminPaymentRow>> listAllPayments() {
        return ResponseEntity.ok(financeQueryService.getAllPayments());
    }

    @GetMapping("/admin/finance/kpis")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<FinanceQueryService.AdminFinanceKpis> financeKpis() {
        return ResponseEntity.ok(financeQueryService.getKpis());
    }

    @GetMapping("/admin/finance/partners-due")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<List<FinanceQueryService.PartnerDueRow>> partnersDue() {
        return ResponseEntity.ok(financeQueryService.getPartnersDue());
    }

    // ------------------------------------------------------------- PARTENAIRE ----

    @GetMapping("/partner/payouts")
    @PreAuthorize("hasRole('CENTRE_FORMATION')")
    public ResponseEntity<List<PayoutDto>> listMyPayouts(Authentication auth) {
        return ResponseEntity.ok(
                payoutService.getPayouts(currentPartnerProfileId(auth)).stream().map(this::toDto).toList());
    }

    /** Inscriptions réglées : part nette du CHU uniquement, jamais la commission. */
    @GetMapping("/partner/payments")
    @PreAuthorize("hasRole('CENTRE_FORMATION')")
    public ResponseEntity<List<FinanceQueryService.PartnerPaymentRow>> myPayments(Authentication auth) {
        return ResponseEntity.ok(financeQueryService.getPartnerPayments(currentPartnerProfileId(auth)));
    }

    @GetMapping("/partner/financial-summary")
    @PreAuthorize("hasRole('CENTRE_FORMATION')")
    public ResponseEntity<PartnerPayoutService.PartnerFinancialSummary> mySummary(Authentication auth) {
        return ResponseEntity.ok(payoutService.getSummary(currentPartnerProfileId(auth)));
    }

    // ---------------------------------------------------------------------------------

    /** Résolu depuis le compte connecté : un partenaire ne consulte jamais les chiffres d'un autre. */
    private UUID currentPartnerProfileId(Authentication auth) {
        UUID userId = UUID.fromString(auth.getPrincipal().toString());
        return partnerProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalStateException("Aucun profil partenaire pour ce compte."))
                .getId();
    }

    private PayoutDto toDto(PartnerPayout payout) {
        return new PayoutDto(
                payout.getId(), payout.getReference(), payout.getTotalAmount(), payout.getCurrency(),
                payout.getStatus().name(), payout.getPeriodStart(), payout.getPeriodEnd(),
                payout.getPaidAt(), payout.getCreatedAt());
    }

    public record PayoutDto(UUID id, String reference, BigDecimal totalAmount, String currency,
                            String status, LocalDate periodStart, LocalDate periodEnd,
                            OffsetDateTime paidAt, OffsetDateTime createdAt) {
    }
}

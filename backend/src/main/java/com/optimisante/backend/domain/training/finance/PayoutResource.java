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
import com.optimisante.backend.config.security.MobilityAdmin;

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
    private final PayoutDossierService dossierService;
    private final SepaVirementService sepaService;

    // ------------------------------------------------------------------ ADMIN ----

    @PostMapping("/admin/partners/{partnerProfileId}/payouts")
    @MobilityAdmin
    public ResponseEntity<PayoutDto> generatePayout(
            @PathVariable UUID partnerProfileId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodStart,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodEnd) {
        return ResponseEntity.ok(toDto(
                payoutService.generatePayout(partnerProfileId, periodStart, periodEnd)));
    }

    @PostMapping("/admin/payouts/{payoutId}/mark-paid")
    @MobilityAdmin
    public ResponseEntity<PayoutDto> markPaid(@PathVariable UUID payoutId) {
        return ResponseEntity.ok(toDto(payoutService.markAsPaid(payoutId)));
    }

    @GetMapping("/admin/partners/{partnerProfileId}/payouts")
    @MobilityAdmin
    public ResponseEntity<List<PayoutDto>> listPayoutsForAdmin(@PathVariable UUID partnerProfileId) {
        return ResponseEntity.ok(payoutService.getPayouts(partnerProfileId).stream().map(this::toDto).toList());
    }

    @GetMapping("/admin/partners/{partnerProfileId}/financial-summary")
    @MobilityAdmin
    public ResponseEntity<PartnerPayoutService.PartnerFinancialSummary> summaryForAdmin(
            @PathVariable UUID partnerProfileId) {
        return ResponseEntity.ok(payoutService.getSummary(partnerProfileId));
    }

    @GetMapping("/admin/payments")
    @MobilityAdmin
    public ResponseEntity<List<FinanceQueryService.AdminPaymentRow>> listAllPayments() {
        return ResponseEntity.ok(financeQueryService.getAllPayments());
    }

    @GetMapping("/admin/finance/kpis")
    @MobilityAdmin
    public ResponseEntity<FinanceQueryService.AdminFinanceKpis> financeKpis() {
        return ResponseEntity.ok(financeQueryService.getKpis());
    }

    @GetMapping("/admin/finance/partners-due")
    @MobilityAdmin
    public ResponseEntity<List<FinanceQueryService.PartnerDueRow>> partnersDue() {
        return ResponseEntity.ok(financeQueryService.getPartnersDue());
    }

    // ----------------------------------------------------- REVERSEMENT PAR DOSSIER ----

    /** Les dossiers d'un CHU, tranche par tranche : ce qui est à reverser, en attente, déjà viré. */
    @GetMapping("/admin/partners/{partnerProfileId}/payout-dossiers")
    @MobilityAdmin
    public ResponseEntity<List<PayoutDossierService.DossierReversement>> payoutDossiers(
            @PathVariable UUID partnerProfileId) {
        return ResponseEntity.ok(dossierService.dossiers(partnerProfileId));
    }

    @GetMapping("/admin/partners/{partnerProfileId}/bank-details")
    @MobilityAdmin
    public ResponseEntity<PayoutDossierService.CoordonneesPartenaire> bankDetails(
            @PathVariable UUID partnerProfileId) {
        return ResponseEntity.ok(dossierService.coordonnees(partnerProfileId));
    }

    @PutMapping("/admin/partners/{partnerProfileId}/bank-details")
    @MobilityAdmin
    public ResponseEntity<PayoutDossierService.CoordonneesPartenaire> saveBankDetails(
            @PathVariable UUID partnerProfileId, @RequestBody BankDetailsRequest request) {
        return ResponseEntity.ok(dossierService.enregistrerCoordonnees(
                partnerProfileId, request.iban(), request.bic(), request.accountHolder()));
    }

    /**
     * Initie le virement d'une tranche, ou d'une sélection de tranches d'un même CHU.
     *
     * <p>Le bordereau est produit dans la foulée, mais dans sa propre transaction : s'il échoue
     * — stockage injoignable —, le reversement reste bien créé et le bordereau se régénère depuis
     * l'écran, plutôt que d'annuler un ordre de virement pour un PDF.</p>
     */
    @PostMapping("/admin/partners/{partnerProfileId}/payouts/by-payments")
    @MobilityAdmin
    public ResponseEntity<PayoutDto> payoutForPayments(
            @PathVariable UUID partnerProfileId, @RequestBody PayoutForPaymentsRequest request) {
        PartnerPayout reversement = payoutService.generatePayoutForPayments(partnerProfileId, request.paymentIds());
        try {
            reversement = payoutService.generateStatement(reversement.getId());
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(PayoutResource.class).error(
                    "Reversement {} créé, mais bordereau non produit : à régénérer", reversement.getReference(), e);
        }
        return ResponseEntity.ok(toDto(reversement));
    }

    /** Ordre de virement SEPA à importer dans la banque. */
    @GetMapping("/admin/payouts/{payoutId}/sepa")
    @MobilityAdmin
    public ResponseEntity<byte[]> sepaTransfer(@PathVariable UUID payoutId) {
        SepaVirementService.FichierSepa fichier = sepaService.ordreDeVirement(payoutId);
        return ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.APPLICATION_XML)
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        org.springframework.http.ContentDisposition.attachment()
                                .filename(fichier.nomFichier()).build().toString())
                .body(fichier.contenu());
    }

    public record BankDetailsRequest(String iban, String bic, String accountHolder) {
    }

    public record PayoutForPaymentsRequest(List<UUID> paymentIds) {
    }

    /** Génère (ou régénère) le relevé PDF d'un reversement. */
    @PostMapping("/admin/payouts/{payoutId}/statement")
    @MobilityAdmin
    public ResponseEntity<PayoutDto> generateStatement(@PathVariable UUID payoutId) {
        return ResponseEntity.ok(toDto(payoutService.generateStatement(payoutId)));
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
                payout.getPaidAt(), payout.getCreatedAt(),
                payout.getStatementS3Key() != null);
    }

    /** `statementAvailable` évite d'exposer la clé de stockage au client : le téléchargement
     *  passe par le endpoint signé, qui contrôle les droits. */
    public record PayoutDto(UUID id, String reference, BigDecimal totalAmount, String currency,
                            String status, LocalDate periodStart, LocalDate periodEnd,
                            OffsetDateTime paidAt, OffsetDateTime createdAt,
                            boolean statementAvailable) {
    }
}

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
import java.util.HashMap;
import java.util.Map;

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
    private final com.optimisante.backend.domain.identity.repository.DoctorProfileRepository doctorProfileRepository;
    private final com.optimisante.backend.domain.document.service.PdfGeneratorService pdfGeneratorService;
    private final com.optimisante.backend.common.storage.StorageService storageService;

    /** Relecture des lignes une fois verrouillées : voir generatePayoutForPayments. */
    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;

    private static final java.time.format.DateTimeFormatter DATE_FORMAT =
            java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");

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

    /**
     * Reverse des encaissements PRÉCIS : une tranche d'un dossier, ou une sélection cochée.
     *
     * <p><b>Pourquoi dossier par dossier.</b> Le reversement par période additionnait tout le dû
     * du CHU en un seul virement. Quand un médecin avait réglé son acompte et un autre son solde,
     * le comptable du CHU recevait une somme ronde sans pouvoir la ventiler par candidat. Ici,
     * chaque virement porte les lignes exactes qu'il règle, et sa référence les nomme.</p>
     *
     * <p>Même garantie que le reversement par période : une ligne déjà rattachée à un reversement
     * n'est jamais reprise. Les deux modes restent donc cohérents entre eux.</p>
     *
     * @throws IllegalArgumentException une ligne n'existe pas ou n'appartient pas à ce partenaire
     * @throws IllegalStateException    une ligne n'est pas reversable (pas encore réglée, déjà
     *                                  reversée, ou hors assiette du CHU)
     */
    @Transactional
    public PartnerPayout generatePayoutForPayments(UUID partnerProfileId, List<UUID> paymentIds) {
        if (paymentIds == null || paymentIds.isEmpty()) {
            throw new IllegalArgumentException("Aucune ligne à reverser n'a été sélectionnée.");
        }
        PartnerProfile partner = partnerProfileRepository.findById(partnerProfileId)
                .orElseThrow(() -> new IllegalArgumentException("Partenaire introuvable"));

        List<UUID> distincts = paymentIds.stream().distinct().toList();
        List<EnrollmentPayment> lignes = paymentRepository.findAllByIdForUpdate(distincts);
        if (lignes.size() != distincts.size()) {
            throw new IllegalArgumentException("Une des lignes sélectionnées n'existe plus.");
        }

        for (EnrollmentPayment ligne : lignes) {
            // Relire APRÈS le verrou : avec open-in-view, Hibernate rendrait sinon la copie lue
            // plus tôt dans la requête, antérieure au virement que l'autre clic vient de créer.
            entityManager.refresh(ligne);
            String dossier = ReferencesReversement.codeDossier(ligne.getEnrollment());

            if (!ligne.getEnrollment().getSession().getTraining().getPartnerProfile().getId()
                    .equals(partnerProfileId)) {
                throw new IllegalArgumentException("Le dossier " + dossier
                        + " ne relève pas de cet établissement.");
            }
            if (ligne.getPaymentType() != PaymentType.TUITION_FEE) {
                // Les options et les frais de dossier restent à Optimi Santé : hors assiette du CHU.
                throw new IllegalStateException("Le dossier " + dossier
                        + " : seuls les frais de formation se reversent au CHU.");
            }
            if (ligne.getStatus() != PaymentStatus.PAID) {
                throw new IllegalStateException("Le dossier " + dossier
                        + " : cette tranche n'est pas encore réglée par le médecin.");
            }
            if (ligne.getPartnerPayout() != null) {
                throw new IllegalStateException("Le dossier " + dossier + " : cette tranche est déjà "
                        + "reversée (" + ligne.getPartnerPayout().getReference() + ").");
            }
        }

        BigDecimal total = lignes.stream()
                .map(EnrollmentPayment::getPartnerPayoutAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        PartnerPayout payout = payoutRepository.save(PartnerPayout.builder()
                .partnerProfile(partner)
                .totalAmount(total)
                .status(PayoutStatus.PENDING)
                .reference(referenceUnique(referencePourLignes(partner, lignes)))
                .build());

        lignes.forEach(l -> l.setPartnerPayout(payout));
        paymentRepository.saveAll(lignes);

        log.info("Reversement {} initié pour le partenaire {} : {} EUR sur {} tranche(s)",
                payout.getReference(), partnerProfileId, total, lignes.size());
        return payout;
    }

    /**
     * Une tranche : « REV-CHUTESTP-3F2A1C-T1 ». Plusieurs : « REV-CHUTESTP-SEL-260915-3D » —
     * la date et le nombre de dossiers, le détail nominatif figurant au bordereau.
     */
    private String referencePourLignes(PartnerProfile partner, List<EnrollmentPayment> lignes) {
        String etablissement = ReferencesReversement.codeEtablissement(partner.getInstitutionName());
        if (lignes.size() == 1) {
            EnrollmentPayment l = lignes.get(0);
            return "REV-" + etablissement + "-" + ReferencesReversement.empreinteDossier(l.getEnrollment())
                    + "-" + ReferencesReversement.codeTranche(l.getInstallment());
        }
        long dossiers = lignes.stream().map(l -> l.getEnrollment().getId()).distinct().count();
        return "REV-" + etablissement + "-SEL-"
                + LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyMMdd"))
                + "-" + dossiers + "D";
    }

    /**
     * La colonne est unique. Deux sélections du même nombre de dossiers le même jour produiraient
     * la même base : on suffixe plutôt que d'échouer au milieu d'un virement.
     */
    private String referenceUnique(String base) {
        String candidate = base;
        for (int n = 2; payoutRepository.existsByReference(candidate); n++) {
            candidate = base + "-" + n;
        }
        return candidate;
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

    /**
     * Produit le relevé PDF d'un reversement et le dépose sur le stockage.
     *
     * <p>Le document ne mentionne ni le montant brut réglé par le médecin, ni la commission
     * d'agence : il est destiné au partenaire, et la même règle de confidentialité que
     * l'écran « Mes revenus » s'y applique. Faire figurer le brut à côté du net publierait
     * la commission par soustraction.</p>
     *
     * <p>Régénérable : relancer l'appel écrase le relevé précédent, ce qui permet de
     * rattraper un dépôt échoué sans créer de doublon.</p>
     */
    @Transactional
    public PartnerPayout generateStatement(UUID payoutId) {
        PartnerPayout payout = payoutRepository.findById(payoutId)
                .orElseThrow(() -> new IllegalArgumentException("Reversement introuvable"));

        List<EnrollmentPayment> lines = paymentRepository.findByPartnerPayoutId(payoutId);

        Map<String, Object> data = new HashMap<>();
        data.put("reference", payout.getReference());
        data.put("issuedDate", formatDate(payout.getCreatedAt()));
        data.put("partnerName", payout.getPartnerProfile().getInstitutionName());
        data.put("partnerAddress", payout.getPartnerProfile().getAddress());
        data.put("period", formatPeriod(payout));
        data.put("totalAmount", formatMoney(payout.getTotalAmount(), payout.getCurrency()));
        data.put("statusLabel", payout.getStatus() == PayoutStatus.PAID
                ? "Virement effectué le " + formatDate(payout.getPaidAt())
                : "Virement en cours de traitement");

        data.put("lines", lines.stream().map(l -> Map.of(
                "doctorName", doctorName(l),
                "dossierCode", ReferencesReversement.codeDossier(l.getEnrollment()),
                "trainingTitle", l.getEnrollment().getSession().getTraining().getTitle(),
                "trancheLabel", ReferencesReversement.libelleTranche(l.getInstallment(), null),
                "paidAt", formatDate(l.getPaidAt()),
                "grossAmount", formatMoney(l.getGrossAmount(), l.getCurrency()),
                "commissionAmount", formatMoney(l.getCommissionAmount(), l.getCurrency()),
                "netAmount", formatMoney(l.getPartnerPayoutAmount(), l.getCurrency())
        )).toList());
        data.put("totalGross", formatMoney(lines.stream().map(EnrollmentPayment::getGrossAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add), payout.getCurrency()));
        data.put("totalCommission", formatMoney(lines.stream().map(EnrollmentPayment::getCommissionAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add), payout.getCurrency()));
        // Un reversement par dossier se reconnaît à sa référence bancaire ; un reversement par
        // période, ancien mode, garde sa ligne « Période couverte ».
        boolean parDossier = payout.getPeriodStart() == null && payout.getPeriodEnd() == null
                && payout.getReference() != null && !payout.getReference().matches("REV-\\d{4}-\\d{2}-.*");
        data.put("bankReference", parDossier ? payout.getReference() : null);
        data.put("creditedAccount", payout.getPartnerProfile().getIban() == null ? null
                : CoordonneesBancaires.masquer(payout.getPartnerProfile().getIban()));

        byte[] pdf = pdfGeneratorService.generatePayoutStatementPdf(data);
        String publicId = storageService.uploadGeneratedPdf(
                pdf, "docs/releves-reversement", "REV-" + payout.getId());

        payout.setStatementS3Key(publicId);
        log.info("Relevé de reversement {} généré ({} ligne(s))", payout.getReference(), lines.size());
        return payoutRepository.save(payout);
    }

    private String doctorName(EnrollmentPayment payment) {
        return doctorProfileRepository.findByUserId(payment.getEnrollment().getDoctor().getId())
                .map(d -> "Dr. " + d.getFirstName() + " " + d.getLastName())
                .orElse(payment.getEnrollment().getDoctor().getEmail());
    }

    private String formatPeriod(PartnerPayout payout) {
        if (payout.getPeriodStart() == null && payout.getPeriodEnd() == null) {
            return "Toutes inscriptions réglées";
        }
        return (payout.getPeriodStart() != null ? formatDate(payout.getPeriodStart()) : "origine")
                + " au "
                + (payout.getPeriodEnd() != null ? formatDate(payout.getPeriodEnd()) : "ce jour");
    }

    private String formatDate(java.time.temporal.TemporalAccessor value) {
        return value == null ? "-" : DATE_FORMAT.format(value);
    }

    /** Format français, séparateur de milliers insécable — lisible dans le PDF. */
    private String formatMoney(BigDecimal amount, String currency) {
        if (amount == null) return "-";
        // Le JDK produit U+202F (espace fine insecable) en Locale.FRANCE, pas U+00A0.
        // Les deux doivent etre normalises : sinon la police du PDF avale le caractere
        // et le separateur de milliers disparait (2720,00 au lieu de 2 720,00).
        return String.format(java.util.Locale.FRANCE, "%,.2f", amount)
                .replace('\u202F', ' ')
                .replace('\u00A0', ' ') + " " + (currency != null ? currency : "EUR");
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

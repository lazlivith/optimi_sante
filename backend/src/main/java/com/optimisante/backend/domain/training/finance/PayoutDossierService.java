package com.optimisante.backend.domain.training.finance;

import com.optimisante.backend.domain.identity.entity.DoctorProfile;
import com.optimisante.backend.domain.identity.entity.PartnerProfile;
import com.optimisante.backend.domain.identity.repository.DoctorProfileRepository;
import com.optimisante.backend.domain.identity.repository.PartnerProfileRepository;
import com.optimisante.backend.domain.training.entity.Enrollment;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Ce que voit l'administration pour reverser à un CHU : ses dossiers, tranche par tranche.
 *
 * <p><b>Lecture seule.</b> Ce service ne crée ni ne modifie rien : il ordonne ce que la base
 * contient déjà. Chaque encaissement de formation y est une ligne distincte — acompte, solde ou
 * règlement intégral —, avec sa commission et la part du CHU calculées à l'encaissement. Le
 * virement lui-même passe par {@link PartnerPayoutService#generatePayoutForPayments}.</p>
 *
 * <p><b>Ce que le CHU ne perçoit pas, et qui figure pourtant ici.</b> Les options (hébergement,
 * transport, assurance) sont affichées à côté de chaque dossier, parce que l'administration a
 * besoin de voir d'où vient la totalité de ce que le médecin a payé. Elles ne sont jamais
 * reversables, et n'apparaissent ni sur le bordereau ni dans le fichier SEPA.</p>
 */
@Service
@RequiredArgsConstructor
public class PayoutDossierService {

    private final EnrollmentPaymentRepository paymentRepository;
    private final PartnerProfileRepository partnerProfileRepository;
    private final DoctorProfileRepository doctorProfileRepository;
    private final EnrollmentPaymentService enrollmentPaymentService;

    /** États d'une tranche, du point de vue du reversement au CHU. */
    public enum EtatTranche {
        /** Réglée par le médecin, pas encore reversée : le bouton de virement est actif. */
        A_REVERSER,
        /** Pas encore réglée : rien à reverser tant que le médecin n'a pas payé. */
        EN_ATTENTE_MEDECIN,
        /** Ordre de virement émis, pas encore confirmé comme exécuté. */
        VIREMENT_INITIE,
        /** Virement confirmé. */
        VIRE,
        ANNULE
    }

    @Transactional(readOnly = true)
    public List<DossierReversement> dossiers(UUID partnerProfileId) {
        PartnerProfile partenaire = partnerProfileRepository.findById(partnerProfileId)
                .orElseThrow(() -> new IllegalArgumentException("Partenaire introuvable"));
        String etablissement = ReferencesReversement.codeEtablissement(partenaire.getInstitutionName());

        List<EnrollmentPayment> encaissements = paymentRepository.findAllForPartner(partnerProfileId);

        Map<UUID, List<EnrollmentPayment>> parDossier = encaissements.stream()
                .collect(Collectors.groupingBy(p -> p.getEnrollment().getId(),
                        LinkedHashMap::new, Collectors.toList()));

        // Noms des médecins en une requête, et non une par dossier.
        List<UUID> medecins = encaissements.stream()
                .map(p -> p.getEnrollment().getDoctor().getId()).distinct().toList();
        Map<UUID, DoctorProfile> profils = doctorProfileRepository.findByUserIdIn(medecins).stream()
                .collect(Collectors.toMap(d -> d.getUser().getId(), Function.identity(), (a, b) -> a));

        List<DossierReversement> resultat = new ArrayList<>();
        for (List<EnrollmentPayment> lignes : parDossier.values()) {
            Enrollment inscription = lignes.get(0).getEnrollment();

            List<EnrollmentPayment> formation = lignes.stream()
                    .filter(l -> l.getPaymentType() == PaymentType.TUITION_FEE)
                    .filter(l -> l.getStatus() == PaymentStatus.PAID || l.getStatus() == PaymentStatus.PENDING)
                    .toList();

            // « Les dossiers des candidats qui ont déjà payé » : au moins une tranche réglée.
            if (formation.stream().noneMatch(l -> l.getStatus() == PaymentStatus.PAID)) {
                continue;
            }

            List<TrancheReversement> tranches = new ArrayList<>(formation.stream()
                    .map(l -> tranche(l, etablissement, inscription))
                    .toList());
            soldeAttendu(inscription, formation).ifPresent(tranches::add);
            tranches.sort(Comparator.comparing(t -> ordre(t.tranche())));

            BigDecimal options = lignes.stream()
                    .filter(l -> l.getPaymentType() == PaymentType.SERVICE_OPTIONS)
                    .filter(l -> l.getStatus() == PaymentStatus.PAID)
                    .map(EnrollmentPayment::getGrossAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            DoctorProfile profil = profils.get(inscription.getDoctor().getId());
            String nom = profil != null
                    ? "Dr. " + profil.getFirstName() + " " + profil.getLastName()
                    : inscription.getDoctor().getEmail();

            resultat.add(new DossierReversement(
                    inscription.getId(),
                    ReferencesReversement.codeDossier(inscription),
                    nom,
                    inscription.getSession().getTraining().getTitle(),
                    inscription.getSession().getStartDate(),
                    options,
                    tranches));
        }

        // Ce qui attend un virement d'abord, puis le plus récemment encaissé.
        resultat.sort(Comparator
                .comparing((DossierReversement d) -> d.tranches().stream()
                        .noneMatch(t -> t.etat() == EtatTranche.A_REVERSER))
                .thenComparing(d -> d.tranches().stream()
                        .map(TrancheReversement::paidAt).filter(Objects::nonNull)
                        .max(Comparator.naturalOrder()).orElse(null),
                        Comparator.nullsLast(Comparator.reverseOrder())));
        return resultat;
    }

    private TrancheReversement tranche(EnrollmentPayment l, String etablissement, Enrollment inscription) {
        EtatTranche etat;
        if (l.getStatus() != PaymentStatus.PAID) {
            etat = EtatTranche.EN_ATTENTE_MEDECIN;
        } else if (l.getPartnerPayout() == null) {
            etat = EtatTranche.A_REVERSER;
        } else {
            etat = switch (l.getPartnerPayout().getStatus()) {
                case PENDING -> EtatTranche.VIREMENT_INITIE;
                case PAID -> EtatTranche.VIRE;
                case CANCELLED -> EtatTranche.ANNULE;
            };
        }
        PartnerPayout reversement = l.getPartnerPayout();
        return new TrancheReversement(
                l.getStatus() == PaymentStatus.PAID ? l.getId() : null,
                trancheDe(l.getInstallment()),
                ReferencesReversement.libelleTranche(l.getInstallment(), null),
                etat,
                l.getGrossAmount(), l.getCommissionRate(), l.getCommissionAmount(), l.getPartnerPayoutAmount(),
                l.getPaidAt(),
                reversement != null ? reversement.getId() : null,
                reversement != null ? reversement.getReference() : null,
                reversement != null && reversement.getStatementS3Key() != null,
                "REV-" + etablissement + "-" + ReferencesReversement.empreinteDossier(inscription)
                        + "-" + ReferencesReversement.codeTranche(l.getInstallment()));
    }

    /**
     * Le solde que le médecin n'a pas encore été appelé à payer : il n'existe pas en base tant que
     * l'échéance n'est pas ouverte, mais le CHU doit savoir qu'il viendra. Montant et commission
     * suivent exactement les règles de l'encaissement réel — l'échéancier de la formation, et le
     * taux déjà appliqué à l'acompte.
     */
    private java.util.Optional<TrancheReversement> soldeAttendu(Enrollment inscription,
                                                                 List<EnrollmentPayment> formation) {
        EnrollmentPayment acompte = formation.stream()
                .filter(l -> l.getInstallment() == PaymentInstallment.DEPOSIT && l.getStatus() == PaymentStatus.PAID)
                .findFirst().orElse(null);
        boolean soldePresent = formation.stream().anyMatch(l -> l.getInstallment() == PaymentInstallment.BALANCE);
        if (acompte == null || soldePresent) {
            return java.util.Optional.empty();
        }
        var echeancier = enrollmentPaymentService.scheduleFor(inscription);
        if (!echeancier.hasBalance()) {
            return java.util.Optional.empty();
        }
        var repartition = FinancialSplitCalculator.calculateSplit(
                echeancier.balanceAmount(), acompte.getCommissionRate());
        return java.util.Optional.of(new TrancheReversement(
                null, "BALANCE", ReferencesReversement.libelleTranche(PaymentInstallment.BALANCE, null),
                EtatTranche.EN_ATTENTE_MEDECIN,
                repartition.grossAmount(), repartition.commissionRate(), repartition.commissionAmount(),
                repartition.partnerPayoutAmount(), null, null, null, false, null));
    }

    private static String trancheDe(PaymentInstallment i) {
        return i == null ? "FULL" : i.name();
    }

    private static int ordre(String tranche) {
        return switch (tranche) {
            case "DEPOSIT" -> 0;
            case "BALANCE" -> 1;
            default -> 2;
        };
    }

    // ------------------------------------------------------------ COORDONNÉES BANCAIRES ----

    @Transactional(readOnly = true)
    public CoordonneesPartenaire coordonnees(UUID partnerProfileId) {
        return versVue(partnerProfileRepository.findById(partnerProfileId)
                .orElseThrow(() -> new IllegalArgumentException("Partenaire introuvable")));
    }

    @Transactional
    public CoordonneesPartenaire enregistrerCoordonnees(UUID partnerProfileId, String iban, String bic,
                                                       String titulaire) {
        PartnerProfile partenaire = partnerProfileRepository.findById(partnerProfileId)
                .orElseThrow(() -> new IllegalArgumentException("Partenaire introuvable"));
        if (titulaire == null || titulaire.isBlank()) {
            throw new IllegalArgumentException("Le titulaire du compte est obligatoire : c'est le nom que "
                    + "la banque du CHU vérifiera.");
        }
        partenaire.setIban(CoordonneesBancaires.validerIban(iban));
        partenaire.setBic(CoordonneesBancaires.validerBic(bic));
        partenaire.setBankAccountHolder(titulaire.trim());
        return versVue(partnerProfileRepository.save(partenaire));
    }

    private static CoordonneesPartenaire versVue(PartnerProfile p) {
        return new CoordonneesPartenaire(
                p.getId(), p.getInstitutionName(), p.getCommissionRate(),
                CoordonneesBancaires.groupesDeQuatre(p.getIban()),
                CoordonneesBancaires.masquer(p.getIban()),
                p.getBic(), p.getBankAccountHolder(),
                p.getIban() != null && p.getBankAccountHolder() != null);
    }

    // ---------------------------------------------------------------------------- VUES ----

    public record DossierReversement(
            UUID enrollmentId, String dossierCode, String doctorName, String trainingTitle,
            LocalDate sessionStart, BigDecimal optionsAmount, List<TrancheReversement> tranches) {
    }

    /**
     * @param paymentId      ligne à reverser ; nul pour une tranche pas encore réglée
     * @param bankReference  référence qu'aura le virement de CETTE seule tranche
     */
    public record TrancheReversement(
            UUID paymentId, String tranche, String trancheLabel, EtatTranche etat,
            BigDecimal grossAmount, BigDecimal commissionRate, BigDecimal commissionAmount,
            BigDecimal netAmount, OffsetDateTime paidAt,
            UUID payoutId, String payoutReference, boolean statementAvailable,
            String bankReference) {
    }

    public record CoordonneesPartenaire(
            UUID partnerProfileId, String institutionName, BigDecimal commissionRate,
            String iban, String ibanMasque, String bic, String titulaire, boolean complet) {
    }
}

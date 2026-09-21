package com.optimisante.backend.domain.training.service;

import com.optimisante.backend.domain.training.dto.OfficialDocumentDtos.VaultDossier;
import com.optimisante.backend.domain.training.dto.OfficialDocumentDtos.VaultEntry;
import com.optimisante.backend.domain.training.dto.OfficialDocumentDtos.VaultEntryState;
import com.optimisante.backend.domain.training.entity.*;
import com.optimisante.backend.domain.training.finance.EnrollmentPaymentService;
import com.optimisante.backend.domain.training.repository.EnrollmentOfficialDocumentRepository;
import com.optimisante.backend.domain.training.repository.EnrollmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static com.optimisante.backend.domain.training.entity.EnrollmentStatus.*;

/**
 * Coffre-fort du médecin, dossier par dossier : ce qu'il peut ouvrir, ce qui s'ouvrira et quand.
 *
 * <p><b>Lecture seule.</b> Rien n'est créé ni modifié ici : le service assemble la convention et
 * l'attestation générées par la plateforme, les documents officiels publiés, et les documents
 * attendus aux étapes suivantes. Montrer ce qui vient — « Kit de départ, débloqué au règlement du
 * solde » — dit au médecin où il en est mieux qu'un coffre vide.</p>
 *
 * <p>L'accès aux documents officiels suit {@link OfficialDocumentService#accesMedecin}, la règle
 * qu'applique aussi le téléchargement.</p>
 */
@Service
@RequiredArgsConstructor
public class DossierVaultService {

    private static final Set<EnrollmentStatus> TERMINAUX = EnumSet.of(REJECTED, CANCELLED);
    private static final String OPTIMI = "Optimi Santé";

    private final EnrollmentRepository enrollmentRepository;
    private final EnrollmentOfficialDocumentRepository officialDocumentRepository;
    private final OfficialDocumentService officialDocumentService;
    private final EnrollmentPaymentService enrollmentPaymentService;

    @Transactional(readOnly = true)
    public List<VaultDossier> pourMedecin(UUID doctorId) {
        List<Enrollment> dossiers = enrollmentRepository.findByDoctorId(doctorId).stream()
                .sorted(Comparator.comparing(Enrollment::getSubmittedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        if (dossiers.isEmpty()) {
            return List.of();
        }

        Map<UUID, List<EnrollmentOfficialDocument>> publies = officialDocumentRepository
                .findByEnrollmentIdInAndStatusOrderByCreatedAtDesc(
                        dossiers.stream().map(Enrollment::getId).toList(), OfficialDocumentStatus.PUBLISHED)
                .stream()
                .collect(Collectors.groupingBy(d -> d.getEnrollment().getId()));

        return dossiers.stream()
                .map(d -> dossier(d, publies.getOrDefault(d.getId(), List.of())))
                .toList();
    }

    private VaultDossier dossier(Enrollment dossier, List<EnrollmentOfficialDocument> documents) {
        EnrollmentStatus statut = dossier.getStatus();
        boolean clos = TERMINAUX.contains(statut);
        boolean acompteRegle = OfficialDocumentService.ACOMPTE_REGLE.contains(statut);
        String etablissement = dossier.getSession().getTraining().getPartnerProfile() != null
                ? dossier.getSession().getTraining().getPartnerProfile().getInstitutionName()
                : "L'établissement";
        String viaOptimi = etablissement + " (via " + OPTIMI + ")";

        // Le reste dû n'est lu qu'une fois par dossier, et seulement si un document du kit le demande.
        BigDecimal[] resteDu = new BigDecimal[1];
        java.util.function.Supplier<BigDecimal> reste = () -> {
            if (resteDu[0] == null) {
                resteDu[0] = enrollmentPaymentService.outstandingTuition(dossier.getId());
            }
            return resteDu[0];
        };

        List<VaultEntry> lignes = new ArrayList<>();

        // Convention tripartite et attestation d'accueil, générées par la plateforme.
        genere(lignes, "convention", "Convention tripartite de formation", "CONVENTION", dossier,
                dossier.getConventionS3Key(), clos, acompteRegle);
        genere(lignes, "attestation", "Attestation d'accueil", "ATTESTATION", dossier,
                dossier.getAttestationS3Key(), clos, acompteRegle);

        if (clos) {
            return vue(dossier, etablissement, lignes);
        }

        List<EnrollmentOfficialDocument> pedagogiques = documents.stream()
                .filter(d -> !d.getCategory().isKitDeDepart()).toList();
        List<EnrollmentOfficialDocument> kit = documents.stream()
                .filter(d -> d.getCategory().isKitDeDepart()).toList();

        pedagogiques.forEach(d -> lignes.add(officiel(d, viaOptimi, reste)));
        if (pedagogiques.stream().noneMatch(d -> d.getCategory() == OfficialDocumentCategory.PROGRAMME)) {
            lignes.add(new VaultEntry("programme-a-venir", OfficialDocumentCategory.PROGRAMME.libelle(),
                    "Pédagogie", viaOptimi, null, VaultEntryState.UPCOMING,
                    acompteRegle
                            ? "En attente du dépôt par l'établissement et de sa vérification."
                            : "Déposé par l'établissement ; disponible après le règlement de l'acompte (60 %).",
                    null, null));
        }

        kit.forEach(d -> lignes.add(officiel(d, OPTIMI, reste)));
        if (kit.isEmpty()) {
            lignes.add(new VaultEntry("kit-a-venir", "Kit de départ : billets, hébergement, contacts",
                    "Kit de départ", OPTIMI, null, VaultEntryState.UPCOMING,
                    "Préparé par Optimi Santé ; débloqué une fois le visa obtenu et le solde (40 %) réglé.",
                    null, null));
        }
        return vue(dossier, etablissement, lignes);
    }

    private static void genere(List<VaultEntry> lignes, String cle, String titre, String type, Enrollment dossier,
                               String stockage, boolean clos, boolean acompteRegle) {
        if (stockage != null && !stockage.isBlank()) {
            lignes.add(new VaultEntry(cle, titre, "Administratif", OPTIMI, null,
                    VaultEntryState.AVAILABLE, null, type, dossier.getId()));
        } else if (!clos) {
            lignes.add(new VaultEntry(cle + "-a-venir", titre, "Administratif", OPTIMI, null, VaultEntryState.UPCOMING,
                    acompteRegle
                            ? "En cours d'émission par Optimi Santé."
                            : "Émise par Optimi Santé après le règlement de l'acompte (60 %).",
                    null, null));
        }
    }

    private VaultEntry officiel(EnrollmentOfficialDocument d, String emetteur,
                                java.util.function.Supplier<BigDecimal> reste) {
        OfficialDocumentService.Acces acces = officialDocumentService.accesMedecin(d, reste);
        OffsetDateTime date = d.getReviewedAt() != null ? d.getReviewedAt() : d.getCreatedAt();
        String emis = d.getIssuer() == OfficialDocumentIssuer.PARTNER ? emetteur : OPTIMI;
        return new VaultEntry(
                "officiel-" + d.getId(), d.getTitle(),
                d.getCategory().isKitDeDepart() ? "Kit de départ" : "Pédagogie",
                emis, date,
                acces.disponible() ? VaultEntryState.AVAILABLE : VaultEntryState.LOCKED,
                acces.motif(),
                acces.disponible() ? "OFFICIAL_DOCUMENT" : null,
                acces.disponible() ? d.getId() : null);
    }

    private static VaultDossier vue(Enrollment dossier, String etablissement, List<VaultEntry> lignes) {
        return new VaultDossier(dossier.getId(), dossier.getSession().getTraining().getTitle(), etablissement,
                dossier.getSession().getStartDate(), dossier.getStatus().name(),
                dossier.getRegistrationType().name(), lignes);
    }
}

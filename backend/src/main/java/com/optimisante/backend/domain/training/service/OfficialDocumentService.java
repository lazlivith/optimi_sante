package com.optimisante.backend.domain.training.service;

import com.optimisante.backend.common.storage.StorageService;
import com.optimisante.backend.domain.identity.repository.DoctorProfileRepository;
import com.optimisante.backend.domain.notification.entity.NotificationSeverity;
import com.optimisante.backend.domain.notification.service.NotificationService;
import com.optimisante.backend.domain.training.dto.OfficialDocumentDtos.OfficialDocumentView;
import com.optimisante.backend.domain.training.entity.*;
import com.optimisante.backend.domain.training.finance.EnrollmentPaymentService;
import com.optimisante.backend.domain.training.repository.EnrollmentOfficialDocumentRepository;
import com.optimisante.backend.domain.training.repository.EnrollmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.optimisante.backend.domain.training.entity.EnrollmentStatus.*;

/**
 * Documents officiels remis au médecin : programme et convention du CHU, kit de départ.
 *
 * <p><b>Le circuit reprend la règle du parcours.</b> Le CHU ne s'adresse jamais directement au
 * médecin : ce qu'il dépose attend la vérification d'Optimi Santé avant d'arriver au coffre-fort.
 * Ce qu'Optimi Santé dépose elle-même est publié d'emblée.</p>
 *
 * <p><b>Le verrou se calcule, il ne se stocke pas.</b> {@link #accesMedecin} lit l'état du
 * dossier et ce qui reste dû à l'instant de la lecture. C'est la même règle qui décide de
 * l'affichage au coffre-fort et du téléchargement : masquer un bouton ne protège rien si le
 * lien direct, lui, reste ouvert.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OfficialDocumentService {

    static final long TAILLE_MAX_OCTETS = 10L * 1024 * 1024;
    private static final Set<String> FORMATS_ACCEPTES = Set.of("application/pdf", "image/jpeg", "image/png");

    /** Le CHU dépose une fois la candidature retenue : avant, il n'y a pas de formation à documenter. */
    private static final Set<EnrollmentStatus> DEPOT_PARTENAIRE_OUVERT = EnumSet.of(
            ACCEPTED_BY_PARTNER, PENDING_TUITION_FEE, CONFIRMED, CONVENTION_ISSUED,
            VISA_SUBMITTED, VISA_GRANTED, READY_TO_START);

    /** Acompte réglé : la place est réservée, les documents pédagogiques s'ouvrent. */
    static final Set<EnrollmentStatus> ACOMPTE_REGLE = EnumSet.of(
            CONFIRMED, CONVENTION_ISSUED, VISA_SUBMITTED, VISA_GRANTED, READY_TO_START);

    /** Le kit sert au départ : il n'a de sens qu'une fois le visa délivré. */
    private static final Set<EnrollmentStatus> VISA_OBTENU = EnumSet.of(VISA_GRANTED, READY_TO_START);

    private static final Set<EnrollmentStatus> TERMINAUX = EnumSet.of(REJECTED, CANCELLED);

    private final EnrollmentOfficialDocumentRepository documentRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final StorageService storageService;
    private final EnrollmentPaymentService enrollmentPaymentService;
    private final NotificationService notificationService;
    private final DoctorProfileRepository doctorProfileRepository;

    /** Ce que peut faire le médecin d'un document publié, et pourquoi pas encore le cas échéant. */
    public record Acces(boolean disponible, String motif) {
        static Acces ouvert() {
            return new Acces(true, null);
        }

        static Acces verrouille(String motif) {
            return new Acces(false, motif);
        }
    }

    // ------------------------------------------------------------------------ DÉPÔTS ----

    @Transactional
    public OfficialDocumentView deposerParPartenaire(UUID enrollmentId, UUID partnerUserId, MultipartFile file,
                                                     OfficialDocumentCategory category, String title) {
        Enrollment dossier = requireEnrollment(enrollmentId);
        requirePartnerOwns(dossier, partnerUserId);
        if (category == null || !category.isDeposableParPartenaire()) {
            throw new IllegalArgumentException("L'établissement dépose le programme officiel ou sa convention de "
                    + "formation. Le kit de départ est préparé par Optimi Santé.");
        }
        if (!DEPOT_PARTENAIRE_OUVERT.contains(dossier.getStatus())) {
            throw new IllegalStateException("Les documents de formation se déposent une fois la candidature "
                    + "acceptée (statut actuel : " + dossier.getStatus() + ").");
        }

        EnrollmentOfficialDocument document = documentRepository.save(EnrollmentOfficialDocument.builder()
                .enrollment(dossier)
                .category(category)
                .title(titre(title, category))
                .storageKey(stocker(file))
                .fileName(file.getOriginalFilename())
                .issuer(OfficialDocumentIssuer.PARTNER)
                .status(OfficialDocumentStatus.PENDING_REVIEW)
                .uploadedBy(partnerUserId)
                .build());

        notificationService.notifyAdmins("OFFICIAL_DOCUMENT", NotificationSeverity.INFO,
                "Document de l'établissement à vérifier",
                institution(dossier) + " a déposé « " + document.getTitle() + " » pour " + medecin(dossier) + ".",
                "/admin/enrollments/" + dossier.getId(), null, "OFFICIAL_DOCUMENT_" + document.getId());
        log.info("Document officiel {} ({}) déposé par le partenaire sur le dossier {}",
                document.getId(), category, enrollmentId);
        return vue(document);
    }

    @Transactional
    public OfficialDocumentView deposerParAdmin(UUID enrollmentId, UUID adminUserId, MultipartFile file,
                                                OfficialDocumentCategory category, String title) {
        Enrollment dossier = requireEnrollment(enrollmentId);
        if (category == null) {
            throw new IllegalArgumentException("Précisez la nature du document.");
        }
        if (TERMINAUX.contains(dossier.getStatus())) {
            throw new IllegalStateException("Ce dossier est clos : aucun document ne peut plus y être ajouté.");
        }

        EnrollmentOfficialDocument document = documentRepository.save(EnrollmentOfficialDocument.builder()
                .enrollment(dossier)
                .category(category)
                .title(titre(title, category))
                .storageKey(stocker(file))
                .fileName(file.getOriginalFilename())
                .issuer(OfficialDocumentIssuer.OPTIMI)
                .status(OfficialDocumentStatus.PUBLISHED)
                .uploadedBy(adminUserId)
                .reviewedBy(adminUserId)
                .reviewedAt(OffsetDateTime.now())
                .build());

        prevenirMedecinSiDisponible(document);
        return vue(document);
    }

    // -------------------------------------------------------------------- VÉRIFICATION ----

    @Transactional
    public OfficialDocumentView verifier(UUID documentId, UUID adminUserId, boolean accepter, String motif) {
        requireDocument(documentId);
        if (!accepter && (motif == null || motif.isBlank())) {
            throw new IllegalArgumentException("Indiquez le motif du refus : l'établissement doit savoir quoi corriger.");
        }
        int verifies = documentRepository.verifierSiEnAttente(documentId,
                accepter ? OfficialDocumentStatus.PUBLISHED : OfficialDocumentStatus.REJECTED,
                accepter ? null : motif.trim(), adminUserId, OffsetDateTime.now());
        if (verifies == 0) {
            throw new IllegalStateException("Ce document a déjà été vérifié.");
        }
        EnrollmentOfficialDocument document = requireDocument(documentId);

        Enrollment dossier = document.getEnrollment();
        if (accepter) {
            prevenirMedecinSiDisponible(document);
        } else if (document.getUploadedBy() != null) {
            notificationService.notifyUser(document.getUploadedBy(), "OFFICIAL_DOCUMENT", NotificationSeverity.WARNING,
                    "Document à corriger",
                    "« " + document.getTitle() + " » (" + medecin(dossier) + ") n'a pas été retenu : " + motif.trim(),
                    "/partner/enrollments", null, null);
        }
        return vue(document);
    }

    /**
     * Retrait d'un document. Le CHU ne retire que ce qu'il a déposé et qui n'est pas encore
     * publié : une fois au coffre-fort du médecin, seul Optimi Santé peut l'en retirer.
     */
    @Transactional
    public void retirer(UUID documentId, UUID userId, boolean parAdmin) {
        EnrollmentOfficialDocument document = requireDocument(documentId);
        if (!parAdmin) {
            requirePartnerOwns(document.getEnrollment(), userId);
            if (document.getIssuer() != OfficialDocumentIssuer.PARTNER) {
                throw new AccessDeniedException("Ce document a été déposé par Optimi Santé.");
            }
            if (document.getStatus() == OfficialDocumentStatus.PUBLISHED) {
                throw new IllegalStateException("Ce document est déjà publié au coffre-fort du médecin : "
                        + "demandez à Optimi Santé de le retirer.");
            }
        }
        String cle = document.getStorageKey();
        documentRepository.delete(document);
        try {
            storageService.deleteDocument(cle);
        } catch (Exception e) {
            // Le document n'est plus référencé : un fichier resté en stockage n'est plus accessible.
            log.warn("Fichier {} non supprimé du stockage : {}", cle, e.getMessage());
        }
    }

    // ------------------------------------------------------------------------ LECTURES ----

    @Transactional(readOnly = true)
    public List<OfficialDocumentView> pourAdmin(UUID enrollmentId) {
        requireEnrollment(enrollmentId);
        return documentRepository.findByEnrollmentIdOrderByCreatedAtDesc(enrollmentId).stream()
                .map(this::vue).toList();
    }

    /** Le CHU voit ses documents pédagogiques, jamais la logistique du séjour du médecin. */
    @Transactional(readOnly = true)
    public List<OfficialDocumentView> pourPartenaire(UUID enrollmentId, UUID partnerUserId) {
        requirePartnerOwns(requireEnrollment(enrollmentId), partnerUserId);
        return documentRepository.findByEnrollmentIdOrderByCreatedAtDesc(enrollmentId).stream()
                .filter(d -> d.getCategory().isDeposableParPartenaire())
                .map(this::vue).toList();
    }

    /**
     * Règle d'accès du médecin à un document publié.
     *
     * @param resteDu ce qui reste dû sur la formation ; lu seulement pour le kit de départ
     */
    public Acces accesMedecin(EnrollmentOfficialDocument document, java.util.function.Supplier<BigDecimal> resteDu) {
        EnrollmentStatus statut = document.getEnrollment().getStatus();
        if (document.getStatus() != OfficialDocumentStatus.PUBLISHED || TERMINAUX.contains(statut)) {
            return Acces.verrouille("Ce document n'est pas disponible.");
        }
        if (!document.getCategory().isKitDeDepart()) {
            return ACOMPTE_REGLE.contains(statut)
                    ? Acces.ouvert()
                    : Acces.verrouille("Disponible après le règlement de l'acompte (60 %).");
        }
        if (!VISA_OBTENU.contains(statut)) {
            return Acces.verrouille("Débloqué une fois le visa obtenu et le solde (40 %) réglé.");
        }
        return resteDu.get().signum() > 0
                ? Acces.verrouille("Débloqué au règlement du solde (40 %).")
                : Acces.ouvert();
    }

    /** Lien de téléchargement : mêmes droits que l'affichage, vérifiés côté serveur. */
    @Transactional(readOnly = true)
    public Telechargement pourTelechargement(UUID documentId, UUID userId, boolean adminMobilite) {
        EnrollmentOfficialDocument document = requireDocument(documentId);
        Enrollment dossier = document.getEnrollment();
        if (adminMobilite) {
            return new Telechargement(document.getStorageKey(), document.getTitle());
        }
        boolean partenaire = dossier.getSession().getTraining().getPartnerProfile().getUser().getId().equals(userId);
        if (partenaire) {
            if (!document.getCategory().isDeposableParPartenaire()) {
                throw new AccessDeniedException("Document réservé au médecin.");
            }
            return new Telechargement(document.getStorageKey(), document.getTitle());
        }
        if (dossier.getDoctor().getId().equals(userId)) {
            Acces acces = accesMedecin(document, () -> enrollmentPaymentService.outstandingTuition(dossier.getId()));
            if (!acces.disponible()) {
                throw new IllegalStateException(acces.motif());
            }
            return new Telechargement(document.getStorageKey(), document.getTitle());
        }
        throw new AccessDeniedException("Ce document ne vous concerne pas.");
    }

    public record Telechargement(String storageKey, String libelle) {
    }

    // ------------------------------------------------------------------------ OUTILS ----

    OfficialDocumentView vue(EnrollmentOfficialDocument d) {
        return new OfficialDocumentView(
                d.getId(), d.getCategory(), d.getCategory().libelle(), d.getCategory().isKitDeDepart(),
                d.getTitle(), d.getFileName(), d.getIssuer(), d.getStatus(), d.getRejectionReason(),
                d.getCreatedAt(), d.getReviewedAt());
    }

    private void prevenirMedecinSiDisponible(EnrollmentOfficialDocument document) {
        Enrollment dossier = document.getEnrollment();
        Acces acces = accesMedecin(document, () -> enrollmentPaymentService.outstandingTuition(dossier.getId()));
        if (acces.disponible()) {
            notificationService.notifyUser(dossier.getDoctor().getId(), "ENROLLMENT_STATUS", NotificationSeverity.SUCCESS,
                    "Nouveau document dans votre coffre-fort",
                    "« " + document.getTitle() + " » est disponible pour votre formation.",
                    "/doctor/enrollments/" + dossier.getId(), null, null);
        }
    }

    private String stocker(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Aucun fichier reçu.");
        }
        if (file.getSize() > TAILLE_MAX_OCTETS) {
            throw new IllegalArgumentException("Le fichier dépasse 10 Mo.");
        }
        if (file.getContentType() == null || !FORMATS_ACCEPTES.contains(file.getContentType())) {
            throw new IllegalArgumentException("Format non accepté : déposez un PDF, un JPG ou un PNG.");
        }
        return storageService.uploadFile(file, "docs/enrollments/official");
    }

    private static String titre(String saisi, OfficialDocumentCategory category) {
        String t = saisi == null ? "" : saisi.trim();
        if (t.isEmpty()) {
            return category.libelle();
        }
        return t.length() > 160 ? t.substring(0, 160) : t;
    }

    private Enrollment requireEnrollment(UUID id) {
        return enrollmentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Dossier introuvable."));
    }

    private EnrollmentOfficialDocument requireDocument(UUID id) {
        return documentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Document introuvable."));
    }

    /** Même règle de propriété que {@code InterviewSchedulingService}. */
    private static void requirePartnerOwns(Enrollment enrollment, UUID partnerUserId) {
        if (!enrollment.getSession().getTraining().getPartnerProfile().getUser().getId().equals(partnerUserId)) {
            throw new AccessDeniedException("Ce dossier ne relève pas de votre établissement.");
        }
    }

    private static String institution(Enrollment dossier) {
        var partenaire = dossier.getSession().getTraining().getPartnerProfile();
        return partenaire == null ? "L'établissement" : partenaire.getInstitutionName();
    }

    private String medecin(Enrollment dossier) {
        return doctorProfileRepository.findByUserId(dossier.getDoctor().getId())
                .map(p -> "Dr " + p.getFirstName() + " " + p.getLastName())
                .orElse(dossier.getDoctor().getEmail());
    }
}

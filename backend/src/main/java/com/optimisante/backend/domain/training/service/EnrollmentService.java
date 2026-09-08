package com.optimisante.backend.domain.training.service;

import com.optimisante.backend.domain.identity.entity.DoctorProfile;
import com.optimisante.backend.domain.identity.entity.User;
import com.optimisante.backend.domain.identity.repository.DoctorProfileRepository;
import com.optimisante.backend.domain.identity.repository.UserRepository;
import com.optimisante.backend.domain.training.dto.DocumentUploadRequestDto;
import com.optimisante.backend.domain.training.dto.EnrollmentDetailDto;
import com.optimisante.backend.domain.training.dto.EnrollmentDocumentResponseDto;
import com.optimisante.backend.domain.training.dto.EnrollmentRequestDto;
import com.optimisante.backend.domain.training.dto.EnrollmentResponseDto;
import com.optimisante.backend.domain.training.dto.TrainingSessionResponseDto;
import com.optimisante.backend.domain.training.entity.Enrollment;
import com.optimisante.backend.domain.training.entity.EnrollmentStatus;
import com.optimisante.backend.domain.training.entity.SessionStatus;
import com.optimisante.backend.domain.training.entity.TrainingSession;
import com.optimisante.backend.domain.training.repository.EnrollmentDocumentRepository;
import com.optimisante.backend.domain.training.repository.EnrollmentRepository;
import com.optimisante.backend.domain.training.repository.TrainingSessionRepository;
import com.optimisante.backend.domain.training.entity.DocumentType;
import com.optimisante.backend.domain.training.entity.EnrollmentDocument;
import org.springframework.web.multipart.MultipartFile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.optimisante.backend.domain.training.entity.EnrollmentTransitions;
import org.springframework.security.access.AccessDeniedException;
import java.time.OffsetDateTime;
import com.optimisante.backend.domain.training.finance.EnrollmentPayment;
import com.optimisante.backend.domain.training.finance.EnrollmentPaymentRepository;
import com.optimisante.backend.domain.training.finance.EnrollmentPaymentService;
import com.optimisante.backend.domain.training.finance.PaymentStatus;
import com.optimisante.backend.domain.training.finance.PaymentType;

@Slf4j
@Service
@RequiredArgsConstructor
public class EnrollmentService {

    private final EnrollmentRepository enrollmentRepository;
    private final EnrollmentPaymentRepository paymentRepository;
    private final EnrollmentPaymentService enrollmentPaymentService;
    private final TrainingSessionRepository trainingSessionRepository;
    private final UserRepository userRepository;
    private final DoctorProfileRepository doctorProfileRepository;
    private final EnrollmentDocumentRepository enrollmentDocumentRepository;
    private final com.optimisante.backend.domain.document.service.PdfGeneratorService pdfGeneratorService;
    private final com.optimisante.backend.common.storage.StorageService storageService;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;

    /** Statuts du suivi de mobilité, seuls pilotables via advanceMobility. */
    private static final java.util.Set<EnrollmentStatus> MOBILITY_STATUSES = java.util.EnumSet.of(
            EnrollmentStatus.CONVENTION_ISSUED, EnrollmentStatus.VISA_SUBMITTED,
            EnrollmentStatus.VISA_GRANTED, EnrollmentStatus.READY_TO_START);

    @Transactional
    public EnrollmentResponseDto createEnrollment(EnrollmentRequestDto dto, UUID doctorId) {
        User doctor = userRepository.findById(doctorId)
                .orElseThrow(() -> new RuntimeException("Doctor not found"));

        if (enrollmentRepository.existsByDoctorIdAndSessionId(doctorId, dto.sessionId())) {
            throw new IllegalStateException("Vous êtes déjà inscrit à cette session");
        }

        TrainingSession session = trainingSessionRepository.findById(dto.sessionId())
                .orElseThrow(() -> new RuntimeException("Session introuvable"));

        if (session.getStatus() != SessionStatus.OPEN) {
            throw new IllegalStateException("Cette session n'accepte plus d'inscriptions");
        }

        // Tente de décrémenter les places de manière atomique
        int updatedRows = trainingSessionRepository.decrementAvailableSeats(session.getId());
        if (updatedRows == 0) {
            throw new IllegalStateException("Plus aucune place disponible pour cette session");
        }

        // Si la mise à jour a réussi, on crée l'inscription
        // Statut d'entrée non repositionné ici : il est porté par @Builder.Default sur
        // l'entité (UNDER_OPTIMI_REVIEW), source unique pour les deux portes d'entrée
        // du médecin — candidature payante et inscription directe.
        Enrollment enrollment = Enrollment.builder()
                .doctor(doctor)
                .session(session)
                .build();

        return toResponseDto(enrollmentRepository.save(enrollment));
    }

    @Transactional
    public EnrollmentResponseDto submitDocuments(UUID enrollmentId, UUID doctorId, DocumentUploadRequestDto dto) {
        Enrollment enrollment = enrollmentRepository.findByIdAndDoctorId(enrollmentId, doctorId)
                .orElseThrow(() -> new RuntimeException("Enrollment not found for this doctor"));

        if (enrollment.getStatus() != EnrollmentStatus.UNDER_OPTIMI_REVIEW
                && enrollment.getStatus() != EnrollmentStatus.ACTION_REQUIRED) {
            throw new IllegalStateException(
                    "Les pièces ne peuvent être déposées que pendant la revue OptimiSanté "
                            + "ou après une demande de correction.");
        }

        if (dto.diplomaUrl() != null)
            enrollment.setDiplomaUrl(dto.diplomaUrl());
        if (dto.medicalBoardRegistrationUrl() != null)
            enrollment.setMedicalBoardRegistrationUrl(dto.medicalBoardRegistrationUrl());
        if (dto.passportUrl() != null)
            enrollment.setPassportUrl(dto.passportUrl());

        // enrollment.setStatus(EnrollmentStatus.UNDER_REVIEW); // L'Admin changera le statut manuellement

        log.info("Documents submitted for enrollment {} by doctor {}", enrollmentId, doctorId);
        return toResponseDto(enrollmentRepository.save(enrollment));
    }

    @Transactional(readOnly = true)
    public List<TrainingSessionResponseDto> getAvailableSessions(UUID trainingId) {
        return trainingSessionRepository.findByTrainingIdAndStatus(trainingId, SessionStatus.OPEN).stream()
                .map(this::toSessionResponseDto)
                .collect(java.util.stream.Collectors.toList());
    }

    @Transactional
    public EnrollmentResponseDto generateConvention(UUID enrollmentId) {
        return toResponseDto(generateConventionInternal(enrollmentId));
    }

    private Enrollment generateConventionInternal(UUID enrollmentId) {
        Enrollment enrollment = enrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new RuntimeException("Enrollment not found"));

        if (enrollment.getStatus() != EnrollmentStatus.CONFIRMED) {
            // Relance manuelle : on repositionne le dossier sur la jonction du cycle, sans
            // repasser par l'automate (c'est une action de rattrapage administratif assumée,
            // pas une transition métier).
            enrollment.setStatus(EnrollmentStatus.CONFIRMED);
        }

        // Fetching DoctorProfile to get valid profile data like names and specialty
        DoctorProfile doctorProfile = doctorProfileRepository.findByUserId(enrollment.getDoctor().getId())
                .orElseThrow(() -> new RuntimeException("Doctor profile not found"));

        // Prepare Data for PDF
        Map<String, Object> data = new java.util.HashMap<>();
        data.put("reference", "CONV-2026-" + enrollment.getId().toString().substring(0, 8).toUpperCase());
        data.put("currentDate",
                java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")));
        data.put("chuName", enrollment.getSession().getLocation());
        data.put("chuAddress", enrollment.getSession().getLocation()); // Simplification

        // Correctly using doctorProfile's fields instead of User's
        data.put("doctorName", "Dr. " + (doctorProfile.getFirstName() != null ? doctorProfile.getFirstName() : "") + " "
                + (doctorProfile.getLastName() != null ? doctorProfile.getLastName() : ""));
        data.put("doctorSpecialty", doctorProfile.getMedicalSpecialty() != null ? doctorProfile.getMedicalSpecialty()
                : "Médecine Générale");
        data.put("doctorEmail", enrollment.getDoctor().getEmail());
        data.put("startDate", enrollment.getSession().getStartDate()
                .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")));
        data.put("endDate", enrollment.getSession().getEndDate()
                .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")));

        // Generate PDF
        byte[] pdfBytes = pdfGeneratorService.generateTripartiteConventionPdf(data);

        // Upload to Cloudinary using raw bytes directly instead of MockMultipartFile
        String fileName = "CONV-2026-" + enrollment.getId();

        try {
            String publicId = storageService.uploadGeneratedPdf(pdfBytes, "docs/conventions", fileName);
            enrollment.setConventionS3Key(publicId);
            log.info("Convention generated and uploaded for enrollment {} with key {}", enrollmentId, publicId);
        } catch (Exception e) {
            log.error("Failed to upload convention PDF to Cloudinary for enrollment {}", enrollmentId, e);
            throw new RuntimeException("Failed to upload convention", e);
        }

        return enrollmentRepository.save(enrollment);
    }

    @Transactional
    public EnrollmentResponseDto generateAttestation(UUID enrollmentId) {
        return toResponseDto(generateAttestationInternal(enrollmentId));
    }

    /**
     * Attestation d'Accueil / Inscription — document consulaire distinct de la convention
     * tripartite (le template {@code attestation-ins.html} et la méthode
     * {@link com.optimisante.backend.domain.document.service.PdfGeneratorService#generateEnrollmentAttestationPdf}
     * existaient déjà dans le projet mais n'étaient jamais appelés nulle part avant ce correctif).
     */
    private Enrollment generateAttestationInternal(UUID enrollmentId) {
        Enrollment enrollment = enrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new RuntimeException("Enrollment not found"));

        DoctorProfile doctorProfile = doctorProfileRepository.findByUserId(enrollment.getDoctor().getId())
                .orElseThrow(() -> new RuntimeException("Doctor profile not found"));

        Map<String, Object> data = new java.util.HashMap<>();
        data.put("enrollmentReference", "INS-2026-" + enrollment.getId().toString().substring(0, 8).toUpperCase());
        data.put("date", java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")));
        data.put("doctorName", "Dr. " + (doctorProfile.getFirstName() != null ? doctorProfile.getFirstName() : "") + " "
                + (doctorProfile.getLastName() != null ? doctorProfile.getLastName() : ""));
        data.put("doctorSpecialty", doctorProfile.getMedicalSpecialty() != null ? doctorProfile.getMedicalSpecialty()
                : "Médecine Générale");
        data.put("passportNumber", doctorProfile.getPassportNumber() != null ? doctorProfile.getPassportNumber() : "Non renseigné");
        data.put("trainingTitle", enrollment.getSession().getTraining().getTitle());
        data.put("hospitalName", enrollment.getSession().getLocation());
        data.put("startDate", enrollment.getSession().getStartDate()
                .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")));
        data.put("endDate", enrollment.getSession().getEndDate()
                .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")));

        byte[] pdfBytes = pdfGeneratorService.generateEnrollmentAttestationPdf(data);
        String fileName = "ATTESTATION-" + enrollment.getId();

        try {
            String publicId = storageService.uploadGeneratedPdf(pdfBytes, "docs/attestations", fileName);
            enrollment.setAttestationS3Key(publicId);
            log.info("Attestation d'accueil générée et uploadée pour l'inscription {} avec la clé {}", enrollmentId, publicId);
        } catch (Exception e) {
            log.error("Échec de l'upload de l'attestation d'accueil pour l'inscription {}", enrollmentId, e);
            throw new RuntimeException("Failed to upload attestation", e);
        }

        return enrollmentRepository.save(enrollment);
    }

    // =====================================================================================
    // Cycle de candidature tripartite (V26) - une méthode par intention métier.
    //
    // Chacune passe par EnrollmentTransitions.assertAllowed(...) : l'automate est le seul
    // arbitre des passages légaux, aucune méthode ne décide seule de ce qu'elle a le droit
    // de faire. Ajouter un état ne se fait donc qu'à un seul endroit.
    // =====================================================================================

    /**
     * Étape de pré-qualification : OptimiSanté a vérifié les pièces et transmet le dossier
     * au partenaire pour décision pédagogique. Le partenaire ne voit rien avant ce passage.
     */
    @Transactional
    public EnrollmentResponseDto submitToPartner(UUID enrollmentId, UUID adminId) {
        Enrollment enrollment = requireEnrollment(enrollmentId);
        EnrollmentTransitions.assertAllowed(enrollment.getStatus(), EnrollmentStatus.SUBMITTED_TO_PARTNER);

        enrollment.setStatus(EnrollmentStatus.SUBMITTED_TO_PARTNER);
        enrollment.setOptimiReviewedAt(OffsetDateTime.now());
        enrollment.setOptimiReviewedBy(adminId);
        enrollment.setActionRequiredNote(null);

        log.info("Dossier {} pré-qualifié par l'admin {} et transmis au partenaire", enrollmentId, adminId);
        return toResponseDto(enrollmentRepository.save(enrollment));
    }

    /**
     * Pièces manquantes ou non conformes : la main repasse au médecin, avec un motif qui lui
     * est affiché. Le dossier n'est pas rejeté, il reste dans le tunnel.
     */
    @Transactional
    public EnrollmentResponseDto requestAction(UUID enrollmentId, UUID adminId, String note) {
        if (note == null || note.isBlank()) {
            throw new IllegalArgumentException("Un motif est obligatoire pour demander des corrections.");
        }
        Enrollment enrollment = requireEnrollment(enrollmentId);
        EnrollmentTransitions.assertAllowed(enrollment.getStatus(), EnrollmentStatus.ACTION_REQUIRED);

        enrollment.setStatus(EnrollmentStatus.ACTION_REQUIRED);
        enrollment.setActionRequiredNote(note.trim());
        enrollment.setOptimiReviewedAt(OffsetDateTime.now());
        enrollment.setOptimiReviewedBy(adminId);

        log.info("Corrections demandées sur le dossier {} par l'admin {}", enrollmentId, adminId);
        return toResponseDto(enrollmentRepository.save(enrollment));
    }

    /** Le médecin a déposé les pièces demandées et resoumet son dossier à la revue. */
    @Transactional
    public EnrollmentResponseDto resubmitAfterAction(UUID enrollmentId, UUID doctorId) {
        Enrollment enrollment = requireEnrollment(enrollmentId);
        if (!enrollment.getDoctor().getId().equals(doctorId)) {
            throw new AccessDeniedException("Ce dossier ne vous appartient pas.");
        }
        EnrollmentTransitions.assertAllowed(enrollment.getStatus(), EnrollmentStatus.UNDER_OPTIMI_REVIEW);

        enrollment.setStatus(EnrollmentStatus.UNDER_OPTIMI_REVIEW);
        enrollment.setActionRequiredNote(null);

        log.info("Dossier {} resoumis par le médecin {} après corrections", enrollmentId, doctorId);
        return toResponseDto(enrollmentRepository.save(enrollment));
    }

    /**
     * Décision pédagogique du partenaire. Remplace reviewAcademic, dont elle reprend le
     * contrôle de propriété de la formation, en y ajoutant la garde de transition, la
     * traçabilité de la date et le motif obligatoire en cas de refus.
     *
     * <p>Une acceptation enchaîne automatiquement sur PENDING_TUITION_FEE : c'est une
     * conséquence mécanique de la décision, pas une action administrative distincte.</p>
     */
    @Transactional
    public EnrollmentResponseDto partnerDecision(UUID enrollmentId, UUID partnerUserId,
                                                 boolean accept, String reason) {
        Enrollment enrollment = requireEnrollment(enrollmentId);

        if (!enrollment.getSession().getTraining().getPartnerProfile().getUser().getId().equals(partnerUserId)) {
            throw new AccessDeniedException("Vous n'êtes pas autorisé à examiner ce dossier.");
        }

        EnrollmentStatus target = accept ? EnrollmentStatus.ACCEPTED_BY_PARTNER : EnrollmentStatus.REJECTED;
        EnrollmentTransitions.assertAllowed(enrollment.getStatus(), target);

        EnrollmentStatus previousStatus = enrollment.getStatus();

        if (!accept && (reason == null || reason.isBlank())) {
            throw new IllegalArgumentException("Un motif est obligatoire pour refuser une candidature.");
        }

        enrollment.setPartnerDecidedAt(OffsetDateTime.now());

        if (accept) {
            enrollment.setStatus(EnrollmentStatus.ACCEPTED_BY_PARTNER);
            // Enchaînement mécanique vers l'attente de paiement, en repassant par l'automate.
            EnrollmentTransitions.assertAllowed(enrollment.getStatus(), EnrollmentStatus.PENDING_TUITION_FEE);
            enrollment.setStatus(EnrollmentStatus.PENDING_TUITION_FEE);
            log.info("Dossier {} accepté par le partenaire {} - en attente du paiement de la formation",
                    enrollmentId, partnerUserId);
        } else {
            enrollment.setStatus(EnrollmentStatus.REJECTED);
            enrollment.setRejectionReason(reason.trim());
            log.info("Dossier {} refusé par le partenaire {}", enrollmentId, partnerUserId);
        }

        Enrollment saved = enrollmentRepository.save(enrollment);

        if (previousStatus != saved.getStatus()) {
            eventPublisher.publishEvent(
                    new com.optimisante.backend.domain.notification.event.NotificationEvents.EnrollmentStatusChanged(
                            saved.getId(), saved.getDoctor().getId(), saved.getDoctor().getEmail(),
                            previousStatus == null ? null : previousStatus.name(), saved.getStatus().name()));
        }

        return toResponseDto(saved);
    }

    /**
     * Le partenaire réclame une pièce complémentaire au lieu de rejeter le dossier.
     *
     * <p>Sans cette sortie, un simple diplôme mal scanné forçait le CHU au refus définitif :
     * le médecin aurait dû reconstituer une candidature entière, et l'équipe OptimiSanté
     * intervenir en base. Le dossier repasse ici sous la responsabilité d'OptimiSanté —
     * conformément au modèle d'agence, le partenaire ne s'adresse jamais directement au
     * médecin. Après correction, le dossier revient en revue puis est re-transmis.</p>
     */
    @Transactional
    public EnrollmentResponseDto requestPartnerCorrection(UUID enrollmentId, UUID partnerUserId,
                                                          String correctionNote) {
        if (correctionNote == null || correctionNote.isBlank()) {
            throw new IllegalArgumentException(
                    "Une note explicative est requise pour demander des pièces complémentaires.");
        }
        Enrollment enrollment = requireEnrollment(enrollmentId);

        if (!enrollment.getSession().getTraining().getPartnerProfile().getUser().getId().equals(partnerUserId)) {
            throw new AccessDeniedException("Vous n'êtes pas autorisé à examiner ce dossier.");
        }
        EnrollmentTransitions.assertAllowed(enrollment.getStatus(), EnrollmentStatus.ACTION_REQUIRED);

        enrollment.setStatus(EnrollmentStatus.ACTION_REQUIRED);
        // Préfixe d'origine : le médecin et l'admin savent qui réclame la pièce.
        enrollment.setActionRequiredNote("Demande du CHU : " + correctionNote.trim());
        enrollment.setPartnerDecidedAt(OffsetDateTime.now());

        log.info("Le partenaire {} demande des pièces complémentaires sur le dossier {}",
                partnerUserId, enrollmentId);
        return toResponseDto(enrollmentRepository.save(enrollment));
    }

    /**
     * Avancement du cycle de mobilité par l'administration (convention, visa, départ).
     * Volontairement bornée aux états post-CONFIRMED : les décisions commerciales ont leurs
     * propres méthodes et ne doivent pas être atteignables par cette porte.
     */
    @Transactional
    public EnrollmentResponseDto advanceMobility(UUID enrollmentId, EnrollmentStatus newStatus) {
        if (!MOBILITY_STATUSES.contains(newStatus)) {
            throw new IllegalArgumentException(
                    "Le statut " + newStatus + " ne relève pas du cycle de mobilité. "
                            + "Utilisez la méthode métier correspondante.");
        }
        Enrollment enrollment = requireEnrollment(enrollmentId);
        EnrollmentTransitions.assertAllowed(enrollment.getStatus(), newStatus);

        enrollment.setStatus(newStatus);
        log.info("Dossier {} avancé au statut de mobilité {}", enrollmentId, newStatus);
        return toResponseDto(enrollmentRepository.save(enrollment));
    }

    /** Annulation administrative : possible depuis tout état non terminal, motif obligatoire. */
    @Transactional
    public EnrollmentResponseDto cancelEnrollment(UUID enrollmentId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Un motif est obligatoire pour annuler un dossier.");
        }
        Enrollment enrollment = requireEnrollment(enrollmentId);
        EnrollmentTransitions.assertAllowed(enrollment.getStatus(), EnrollmentStatus.CANCELLED);

        enrollment.setStatus(EnrollmentStatus.CANCELLED);
        enrollment.setRejectionReason(reason.trim());

        log.info("Dossier {} annulé : {}", enrollmentId, reason);
        return toResponseDto(enrollmentRepository.save(enrollment));
    }

    /**
     * Confirme l'encaissement des frais de formation (appelé par le webhook Stripe) :
     * solde la ligne du registre, fait passer le dossier en CONFIRMED, puis déclenche
     * l'émission des documents.
     *
     * <p><b>Idempotent</b> : un webhook rejoué par Stripe — ce qui arrive normalement en
     * production — ne doit ni encaisser deux fois, ni régénérer les documents. Le contrôle
     * applicatif ci-dessous est doublé en base par l'index unique partiel
     * {@code uq_enrollment_paid_tuition}.</p>
     */
    @Transactional
    public EnrollmentResponseDto confirmTuitionPayment(UUID enrollmentId, String checkoutSessionId,
                                                       String paymentIntentId) {
        Enrollment enrollment = requireEnrollment(enrollmentId);

        if (paymentRepository.findByEnrollmentIdAndPaymentTypeAndStatus(
                enrollmentId, PaymentType.TUITION_FEE, PaymentStatus.PAID).isPresent()) {
            log.info("Webhook rejoué pour le dossier {} : frais de formation déjà encaissés, ignoré.",
                    enrollmentId);
            return toResponseDto(enrollment);
        }

        EnrollmentPayment payment = paymentRepository
                .findByEnrollmentIdAndPaymentTypeAndStatus(
                        enrollmentId, PaymentType.TUITION_FEE, PaymentStatus.PENDING)
                .orElseGet(() -> {
                    // Filet : le paiement a abouti chez Stripe sans ligne en attente côté
                    // plateforme (session ouverte puis base restaurée, par exemple). On
                    // reconstruit la ligne plutôt que de perdre la trace d'un encaissement réel.
                    log.warn("Aucune ligne de paiement en attente pour le dossier {} : reconstruction.",
                            enrollmentId);
                    return enrollmentPaymentService.openTuitionPayment(enrollmentId);
                });

        payment.setStatus(PaymentStatus.PAID);
        payment.setPaidAt(OffsetDateTime.now());
        payment.setStripeCheckoutSessionId(checkoutSessionId);
        payment.setStripePaymentIntentId(paymentIntentId);
        paymentRepository.save(payment);

        EnrollmentTransitions.assertAllowed(enrollment.getStatus(), EnrollmentStatus.CONFIRMED);
        enrollment.setStatus(EnrollmentStatus.CONFIRMED);
        enrollmentRepository.save(enrollment);

        log.info("Formation réglée pour le dossier {} : {} EUR (commission {} EUR, partenaire {} EUR)",
                enrollmentId, payment.getGrossAmount(), payment.getCommissionAmount(),
                payment.getPartnerPayoutAmount());

        return toResponseDto(issueEnrollmentDocuments(enrollmentId));
    }

    /**
     * Émission des documents à la confirmation de l'inscription, puis avancement vers
     * CONVENTION_ISSUED.
     *
     * <p><b>Isolation stricte</b> : chaque génération vit dans son propre {@code try/catch}.
     * Un échec de rendu PDF ou d'upload Cloudinary ne doit jamais annuler l'encaissement
     * déjà réalisé ni empêcher l'autre document d'être produit. Le dossier reste alors en
     * CONFIRMED et l'administration relance la génération manuellement.</p>
     */
    private Enrollment issueEnrollmentDocuments(UUID enrollmentId) {
        Enrollment result = requireEnrollment(enrollmentId);
        boolean conventionOk = false;

        try {
            result = generateConventionInternal(enrollmentId);
            conventionOk = true;
        } catch (Exception e) {
            log.error("Dossier {} confirmé, mais la génération de la convention tripartite a échoué "
                    + "— à relancer depuis l'administration.", enrollmentId, e);
        }

        try {
            result = generateAttestationInternal(enrollmentId);
        } catch (Exception e) {
            log.error("Dossier {} confirmé, mais la génération de l'attestation d'accueil a échoué "
                    + "— à relancer depuis l'administration.", enrollmentId, e);
        }

        // Le dossier n'avance que si la convention existe réellement : afficher
        // « Convention émise » sans convention serait un mensonge pour le médecin.
        if (conventionOk && result.getStatus() == EnrollmentStatus.CONFIRMED) {
            EnrollmentTransitions.assertAllowed(result.getStatus(), EnrollmentStatus.CONVENTION_ISSUED);
            result.setStatus(EnrollmentStatus.CONVENTION_ISSUED);
            result = enrollmentRepository.save(result);
            log.info("Dossier {} avancé automatiquement à CONVENTION_ISSUED", enrollmentId);
        }

        return result;
    }

    /**
     * Tarif de la formation, ou {@code null} s'il n'est pas configuré. Volontairement
     * silencieux : l'écran de suivi doit rester consultable même si un tarif manque —
     * c'est l'ouverture du paiement, et elle seule, qui doit alors échouer explicitement.
     */
    private java.math.BigDecimal resolveTuitionAmountQuietly(Enrollment enrollment) {
        try {
            return enrollmentPaymentService.resolveTuitionAmount(enrollment);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private Enrollment requireEnrollment(UUID enrollmentId) {
        return enrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new IllegalArgumentException("Dossier introuvable"));
    }

    @Transactional
    public EnrollmentResponseDto updateEnrollmentStatus(UUID enrollmentId, EnrollmentStatus newStatus) {
        Enrollment enrollment = enrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new RuntimeException("Enrollment not found"));

        // Cette méthode générique reste le point d'entrée de l'écran d'administration
        // (bouton « Passer à l'étape suivante »). Elle est désormais soumise au MÊME
        // automate que les méthodes métier : sans cette garde, l'interface aurait
        // contourné toutes les règles posées par EnrollmentTransitions.
        EnrollmentTransitions.assertAllowed(enrollment.getStatus(), newStatus);

        boolean wasNotConfirmed = enrollment.getStatus() != EnrollmentStatus.CONFIRMED;
        EnrollmentStatus previousStatus = enrollment.getStatus();
        enrollment.setStatus(newStatus);

        Enrollment savedEnrollment = enrollmentRepository.save(enrollment);
        log.info("Enrollment {} status updated to {}", enrollmentId, newStatus);

        if (previousStatus != newStatus) {
            eventPublisher.publishEvent(
                    new com.optimisante.backend.domain.notification.event.NotificationEvents.EnrollmentStatusChanged(
                            savedEnrollment.getId(), savedEnrollment.getDoctor().getId(),
                            savedEnrollment.getDoctor().getEmail(),
                            previousStatus == null ? null : previousStatus.name(), newStatus.name()));
        }

        // --- SPRINT 4: Génération automatique de la convention tripartite ---
        // Volontairement non bloquant : le changement de statut est une décision administrative
        // qui ne doit jamais être annulée par un échec de la génération de la convention (ex.
        // upload Cloudinary indisponible pour les fichiers "raw" — limitation externe déjà
        // documentée dans le journal). Avant ce correctif, une exception ici faisait échouer
        // TOUTE la transaction @Transactional et annulait silencieusement le changement de
        // statut lui-même (l'admin voyait une erreur — puis une déconnexion, cf. absence
        // historique de gestionnaire d'exceptions global — sans que rien n'ait réellement changé
        // en base). L'admin peut relancer la génération manuellement une fois le problème résolu
        // via POST /admin/enrollments/{id}/generate-convention.
        if (newStatus == EnrollmentStatus.CONFIRMED && wasNotConfirmed) {
            savedEnrollment = issueEnrollmentDocuments(enrollmentId);
        }

        return toResponseDto(savedEnrollment);
    }

    @Transactional(readOnly = true)
    /**
     * Dossiers visibles par un partenaire.
     *
     * <p>Le filtrage est fait ICI, côté serveur, et non à l'affichage : un dossier encore en
     * pré-qualification chez OptimiSanté ne doit pas être exposé au CHU par l'API, même à un
     * appelant qui contournerait l'interface. C'est la traduction technique du modèle
     * d'agence — le partenaire ne voit que ce qui lui a été transmis.</p>
     */
    public List<Enrollment> getPartnerEnrollments(UUID partnerUserId) {
        return filterVisibleToPartner(
                enrollmentRepository.findBySessionTrainingPartnerProfileUserId(partnerUserId));
    }

    /** Étapes encore internes à OptimiSanté : jamais exposées au partenaire. */
    private static final java.util.Set<EnrollmentStatus> HIDDEN_FROM_PARTNER = java.util.EnumSet.of(
            EnrollmentStatus.UNDER_OPTIMI_REVIEW,
            EnrollmentStatus.ACTION_REQUIRED);

    private List<Enrollment> filterVisibleToPartner(List<Enrollment> enrollments) {
        return enrollments.stream()
                .filter(e -> !HIDDEN_FROM_PARTNER.contains(e.getStatus()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Enrollment> getPartnerEnrollments(UUID partnerUserId, UUID trainingId) {
        if (trainingId == null) {
            return getPartnerEnrollments(partnerUserId);
        }
        return filterVisibleToPartner(
                enrollmentRepository.findBySessionTrainingPartnerProfileUserIdAndSessionTrainingId(
                        partnerUserId, trainingId));
    }

    @Transactional
    public EnrollmentDocumentResponseDto uploadEnrollmentDocument(UUID enrollmentId, UUID userId, MultipartFile file, DocumentType documentType) {
        Enrollment enrollment = enrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new RuntimeException("Enrollment not found"));

        // RBAC Check: Ensure the user is either ADMIN or the doctor owner of this enrollment
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        boolean isAdmin = user.getRole() == com.optimisante.backend.domain.identity.entity.Role.ADMIN
                || user.getRole() == com.optimisante.backend.domain.identity.entity.Role.SUPER_ADMIN;
        if (!isAdmin && !enrollment.getDoctor().getId().equals(userId)) {
            throw new RuntimeException("Unauthorized to upload documents for this enrollment");
        }

        String publicId = storageService.uploadFile(file, "docs/enrollments");

        EnrollmentDocument document = EnrollmentDocument.builder()
                .enrollment(enrollment)
                .documentType(documentType)
                .cloudinaryPublicId(publicId)
                .isVerified(false)
                .build();

        EnrollmentDocument saved = enrollmentDocumentRepository.save(document);
        return EnrollmentDocumentResponseDto.builder()
                .id(saved.getId())
                .documentType(saved.getDocumentType().name())
                .isVerified(saved.getIsVerified())
                .uploadedAt(saved.getUploadedAt())
                .build();
    }

    @Transactional(readOnly = true)
    public EnrollmentDetailDto getEnrollmentDetailForDoctor(UUID enrollmentId, UUID doctorId) {
        Enrollment enrollment = enrollmentRepository.findByIdAndDoctorId(enrollmentId, doctorId)
                .orElseThrow(() -> new RuntimeException("Enrollment not found for this doctor"));
        return toDetailDto(enrollment, false);
    }

    @Transactional(readOnly = true)
    public List<EnrollmentDetailDto> getMyEnrollments(UUID doctorId) {
        return enrollmentRepository.findByDoctorId(doctorId).stream()
                .map(enrollment -> toDetailDto(enrollment, false))
                .collect(java.util.stream.Collectors.toList());
    }

    @Transactional(readOnly = true)
    public EnrollmentDetailDto getEnrollmentDetailForAdmin(UUID enrollmentId) {
        Enrollment enrollment = enrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new RuntimeException("Enrollment not found"));
        return toDetailDto(enrollment, true);
    }

    @Transactional(readOnly = true)
    public List<EnrollmentDetailDto> getAllEnrollmentsForAdmin() {
        return enrollmentRepository.findAll().stream()
                .map(enrollment -> toDetailDto(enrollment, true))
                .collect(java.util.stream.Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<EnrollmentDocument> getEnrollmentDocuments(UUID enrollmentId) {
        return enrollmentDocumentRepository.findByEnrollmentId(enrollmentId);
    }

    @Transactional(readOnly = true)
    public List<EnrollmentDocument> getEnrollmentDocumentsForPartner(UUID enrollmentId, UUID partnerUserId) {
        Enrollment enrollment = enrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new RuntimeException("Dossier introuvable"));

        // Le partenaire ne peut consulter les pièces que des dossiers rattachés à ses propres formations
        // (revue académique : "consultation sécurisée du profil du médecin et de ses pièces médicales").
        if (!enrollment.getSession().getTraining().getPartnerProfile().getUser().getId().equals(partnerUserId)) {
            throw new RuntimeException("Vous n'êtes pas autorisé à consulter ce dossier");
        }

        return enrollmentDocumentRepository.findByEnrollmentId(enrollmentId);
    }

    private EnrollmentResponseDto toResponseDto(Enrollment enrollment) {
        return EnrollmentResponseDto.builder()
                .id(enrollment.getId())
                .status(enrollment.getStatus().name())
                .diplomaUrl(enrollment.getDiplomaUrl())
                .medicalBoardRegistrationUrl(enrollment.getMedicalBoardRegistrationUrl())
                .passportUrl(enrollment.getPassportUrl())
                .submittedAt(enrollment.getSubmittedAt())
                .build();
    }

    private TrainingSessionResponseDto toSessionResponseDto(TrainingSession session) {
        return TrainingSessionResponseDto.builder()
                .id(session.getId())
                .trainingId(session.getTraining().getId())
                .trainingTitle(session.getTraining().getTitle())
                .startDate(session.getStartDate())
                .endDate(session.getEndDate())
                .capacity(session.getCapacity())
                .availableSeats(session.getAvailableSeats())
                .location(session.getLocation())
                .price(session.getPrice())
                .status(session.getStatus().name())
                .createdAt(session.getCreatedAt())
                .build();
    }

    private EnrollmentDetailDto toDetailDto(Enrollment enrollment, boolean includeDoctorName) {
        EnrollmentDetailDto.EnrollmentDetailDtoBuilder builder = EnrollmentDetailDto.builder()
                .id(enrollment.getId())
                .status(enrollment.getStatus().name())
                .trainingTitle(enrollment.getSession().getTraining().getTitle())
                .submittedAt(enrollment.getSubmittedAt())
                .diplomaUrl(enrollment.getDiplomaUrl())
                .medicalBoardRegistrationUrl(enrollment.getMedicalBoardRegistrationUrl())
                .passportUrl(enrollment.getPassportUrl())
                .conventionS3Key(enrollment.getConventionS3Key())
                .attestationS3Key(enrollment.getAttestationS3Key())
                .hostInstitution(enrollment.getSession().getLocation())
                .actionRequiredNote(enrollment.getActionRequiredNote())
                .rejectionReason(enrollment.getRejectionReason())
                // Montant résolu depuis la session (ou le tarif de la formation) : c'est ce
                // que le médecin devra régler, affiché avant même l'ouverture du paiement.
                .tuitionAmount(resolveTuitionAmountQuietly(enrollment));

        if (includeDoctorName) {
            String doctorEmail = enrollment.getDoctor().getEmail();
            String doctorName = doctorProfileRepository.findByUserId(enrollment.getDoctor().getId())
                    .map(p -> "Dr. " + p.getFirstName() + " " + p.getLastName())
                    .orElse("Dr. " + doctorEmail);
            builder.doctorName(doctorName).doctorEmail(doctorEmail);
        }

        return builder.build();
    }
}

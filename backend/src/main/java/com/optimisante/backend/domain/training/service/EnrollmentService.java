package com.optimisante.backend.domain.training.service;

import com.optimisante.backend.domain.identity.entity.DoctorProfile;
import com.optimisante.backend.domain.document.recu.MotifPaiement;
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
import java.util.stream.Collectors;
import com.optimisante.backend.domain.training.entity.EnrollmentTransitions;
import org.springframework.security.access.AccessDeniedException;
import java.time.OffsetDateTime;
import com.optimisante.backend.domain.training.finance.EnrollmentPayment;
import com.optimisante.backend.domain.training.finance.EnrollmentPaymentRepository;
import com.optimisante.backend.domain.training.finance.EnrollmentPaymentService;
import com.optimisante.backend.domain.training.finance.PaymentStatus;
import com.optimisante.backend.domain.training.finance.PaymentInstallment;
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

        Enrollment saved = enrollmentRepository.save(enrollment);

        // Sans cet evenement, un dossier deposé n'apparaissait que si un administrateur
        // pensait de lui-meme a ouvrir la liste : rien ne signalait son arrivee.
        eventPublisher.publishEvent(new com.optimisante.backend.domain.notification.event.NotificationEvents.EnrollmentSubmitted(
                saved.getId(), doctorDisplayName(saved), doctor.getEmail(),
                saved.getSession().getTraining().getTitle()));

        return toResponseDto(saved);
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

        // Les trois pieces de la candidature etaient jusqu'ici ecrites UNIQUEMENT dans ces
        // colonnes, alors que le coffre-fort (admin, CHU, medecin) ne lit que la table
        // enrollment_documents : diplome, passeport et attestation d'ordre etaient donc
        // invisibles et non telechargeables pour ceux qui doivent instruire le dossier.
        // On les inscrit desormais aussi comme pieces du coffre-fort, seule source consultee.
        registerVaultDocument(enrollment, DocumentType.DIPLOMA, dto.diplomaUrl());
        registerVaultDocument(enrollment, DocumentType.MEDICAL_COUNCIL_CERT, dto.medicalBoardRegistrationUrl());
        registerVaultDocument(enrollment, DocumentType.PASSPORT, dto.passportUrl());

        log.info("Documents submitted for enrollment {} by doctor {}", enrollmentId, doctorId);
        Enrollment saved = enrollmentRepository.save(enrollment);

        eventPublisher.publishEvent(new com.optimisante.backend.domain.notification.event.NotificationEvents.EnrollmentDocumentsSubmitted(
                saved.getId(), doctorDisplayName(saved),
                saved.getSession().getTraining().getTitle()));

        return toResponseDto(saved);
    }

    /**
     * Inscrit une piece de candidature au coffre-fort, ou met a jour la reference existante
     * si le medecin redepose la meme piece apres une demande de correction — sans quoi un
     * dossier corrige plusieurs fois accumulerait des doublons dans la liste de l'admin.
     */
    private void registerVaultDocument(Enrollment enrollment, DocumentType type, String publicId) {
        if (publicId == null || publicId.isBlank()) {
            return;
        }
        EnrollmentDocument document = enrollmentDocumentRepository
                .findFirstByEnrollmentIdAndDocumentType(enrollment.getId(), type)
                .orElseGet(() -> EnrollmentDocument.builder()
                        .enrollment(enrollment)
                        .documentType(type)
                        .isVerified(false)
                        .build());
        document.setCloudinaryPublicId(publicId);
        enrollmentDocumentRepository.save(document);
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
            eventPublisher.publishEvent(new com.optimisante.backend.domain.notification.event.NotificationEvents.EnrollmentDocumentIssued(
                    enrollment.getId(), enrollment.getDoctor().getId(), enrollment.getDoctor().getEmail(),
                    "convention tripartite", partnerUserId(enrollment), doctorDisplayName(enrollment)));
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
            eventPublisher.publishEvent(new com.optimisante.backend.domain.notification.event.NotificationEvents.EnrollmentDocumentIssued(
                    enrollment.getId(), enrollment.getDoctor().getId(), enrollment.getDoctor().getEmail(),
                    "attestation d'accueil", null, doctorDisplayName(enrollment)));
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
        Enrollment saved = enrollmentRepository.save(enrollment);

        eventPublisher.publishEvent(new com.optimisante.backend.domain.notification.event.NotificationEvents.EnrollmentSubmittedToPartner(
                saved.getId(), saved.getDoctor().getId(), saved.getDoctor().getEmail(),
                doctorDisplayName(saved), saved.getSession().getTraining().getTitle(),
                partnerUserId(saved), institutionName(saved)));

        return toResponseDto(saved);
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
        Enrollment saved = enrollmentRepository.save(enrollment);

        // Le motif voyage AVEC l'evenement : le medecin doit savoir quelle piece corriger,
        // pas seulement que son dossier a change d'etat.
        eventPublisher.publishEvent(new com.optimisante.backend.domain.notification.event.NotificationEvents.EnrollmentActionRequired(
                saved.getId(), saved.getDoctor().getId(), saved.getDoctor().getEmail(),
                saved.getActionRequiredNote()));

        return toResponseDto(saved);
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
        Enrollment saved = enrollmentRepository.save(enrollment);

        eventPublisher.publishEvent(new com.optimisante.backend.domain.notification.event.NotificationEvents.EnrollmentResubmitted(
                saved.getId(), doctorDisplayName(saved),
                saved.getSession().getTraining().getTitle()));

        return toResponseDto(saved);
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

        eventPublisher.publishEvent(new com.optimisante.backend.domain.notification.event.NotificationEvents.PartnerDecisionMade(
                saved.getId(), saved.getDoctor().getId(), saved.getDoctor().getEmail(),
                doctorDisplayName(saved), institutionName(saved), accept, reason));

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
        Enrollment saved = enrollmentRepository.save(enrollment);

        eventPublisher.publishEvent(new com.optimisante.backend.domain.notification.event.NotificationEvents.PartnerCorrectionRequested(
                saved.getId(), saved.getDoctor().getId(), doctorDisplayName(saved),
                saved.getDoctor().getEmail(), institutionName(saved), saved.getActionRequiredNote()));

        return toResponseDto(saved);
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
        Enrollment saved = enrollmentRepository.save(enrollment);

        eventPublisher.publishEvent(new com.optimisante.backend.domain.notification.event.NotificationEvents.MobilityAdvanced(
                saved.getId(), saved.getDoctor().getId(), saved.getDoctor().getEmail(),
                newStatus.name()));

        return toResponseDto(saved);
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
        Enrollment saved = enrollmentRepository.save(enrollment);

        eventPublisher.publishEvent(new com.optimisante.backend.domain.notification.event.NotificationEvents.EnrollmentCancelled(
                saved.getId(), saved.getDoctor().getId(), saved.getDoctor().getEmail(),
                doctorDisplayName(saved), partnerUserId(saved), saved.getRejectionReason()));

        return toResponseDto(saved);
    }

    /**
     * Confirme l'echeance qui <b>reserve la place</b> : l'acompte, ou le reglement unique d'une
     * session ouverte avant la mise en place de l'echeancier (V45).
     *
     * <p>Les deux cas se traitent identiquement — ils font passer le dossier a
     * {@code CONFIRMED} et declenchent l'emission des documents. Seule change la ligne du
     * registre qu'ils soldent, et donc le montant.</p>
     *
     * <p><b>Idempotent</b> : un webhook rejoue par Stripe — ce qui arrive normalement en
     * production — ne doit ni encaisser deux fois, ni regenerer les documents. Le controle
     * applicatif ci-dessous est double en base par l'index unique partiel
     * {@code uq_enrollment_paid_tuition_installment}.</p>
     */
    @Transactional
    public EnrollmentResponseDto confirmTuitionPayment(UUID enrollmentId, String checkoutSessionId,
                                                       String paymentIntentId,
                                                       PaymentInstallment rang) {
        Enrollment enrollment = requireEnrollment(enrollmentId);

        if (enrollmentPaymentService.findPaidTuition(enrollmentId, rang).isPresent()) {
            log.info("Webhook rejoué pour le dossier {} : échéance {} déjà encaissée, ignoré.",
                    enrollmentId, rang);
            return toResponseDto(enrollment);
        }

        EnrollmentPayment payment = enrollmentPaymentService
                .findPendingTuition(enrollmentId, rang)
                .orElseGet(() -> {
                    // Filet : le paiement a abouti chez Stripe sans ligne en attente côté
                    // plateforme (session ouverte puis base restaurée, par exemple). On
                    // reconstruit la ligne plutôt que de perdre la trace d'un encaissement réel.
                    //
                    // Réservé à l'acompte : une session au prix plein date d'avant l'échéancier,
                    // et rouvrir sa ligne au tarif d'aujourd'hui inscrirait 60 % là où 100 % ont
                    // été prélevés. Sans ligne, l'encaissement est signalé pour rattachement
                    // manuel plutôt que reconstruit de travers.
                    if (rang != PaymentInstallment.DEPOSIT) {
                        throw new IllegalStateException(
                                "Aucune ligne " + rang + " en attente sur le dossier " + enrollmentId
                                + " : encaissement à rattacher manuellement.");
                    }
                    log.warn("Aucune ligne d'acompte en attente pour le dossier {} : reconstruction.",
                            enrollmentId);
                    return enrollmentPaymentService.openTuitionDeposit(enrollmentId);
                });

        solder(payment, checkoutSessionId, paymentIntentId);

        EnrollmentTransitions.assertAllowed(enrollment.getStatus(), EnrollmentStatus.CONFIRMED);
        enrollment.setStatus(EnrollmentStatus.CONFIRMED);
        enrollmentRepository.save(enrollment);

        log.info("Échéance {} réglée pour le dossier {} : {} EUR "
                        + "(commission {} EUR, partenaire {} EUR)",
                rang, enrollmentId, payment.getGrossAmount(), payment.getCommissionAmount(),
                payment.getPartnerPayoutAmount());

        eventPublisher.publishEvent(new com.optimisante.backend.domain.notification.event.NotificationEvents.TuitionPaid(
                enrollment.getId(), enrollment.getDoctor().getId(), doctorDisplayName(enrollment),
                enrollment.getSession().getTraining().getTitle(), partnerUserId(enrollment),
                payment.getGrossAmount()));

        return toResponseDto(issueEnrollmentDocuments(enrollmentId));
    }

    /**
     * Confirme le <b>solde</b> de formation, appele a la delivrance du visa.
     *
     * <p><b>Aucun statut n'est touche.</b> Le dossier est deja au-dela de la reservation : le
     * solde acquitte une dette, il ne fait pas franchir une etape. Faire avancer l'automate ici
     * ferait passer un dossier a « pret a demarrer » sur un simple encaissement, sans que
     * l'administration l'ait constate.</p>
     *
     * <p>Idempotent, comme l'acompte.</p>
     */
    @Transactional
    public EnrollmentResponseDto confirmTuitionBalance(UUID enrollmentId, String checkoutSessionId,
                                                       String paymentIntentId) {
        Enrollment enrollment = requireEnrollment(enrollmentId);

        if (enrollmentPaymentService.findPaidTuition(enrollmentId, PaymentInstallment.BALANCE).isPresent()) {
            log.info("Webhook rejoué pour le dossier {} : solde déjà encaissé, ignoré.", enrollmentId);
            return toResponseDto(enrollment);
        }

        EnrollmentPayment payment = enrollmentPaymentService
                .findPendingTuition(enrollmentId, PaymentInstallment.BALANCE)
                .orElseGet(() -> {
                    log.warn("Aucune ligne de solde en attente pour le dossier {} : reconstruction.",
                            enrollmentId);
                    return enrollmentPaymentService.openTuitionBalance(enrollmentId);
                });

        solder(payment, checkoutSessionId, paymentIntentId);

        log.info("Solde de formation réglé pour le dossier {} : {} EUR "
                        + "(commission {} EUR, partenaire {} EUR). Statut inchangé : {}.",
                enrollmentId, payment.getGrossAmount(), payment.getCommissionAmount(),
                payment.getPartnerPayoutAmount(), enrollment.getStatus());

        return toResponseDto(enrollment);
    }

    private void solder(EnrollmentPayment payment, String checkoutSessionId, String paymentIntentId) {
        payment.setStatus(PaymentStatus.PAID);
        payment.setPaidAt(OffsetDateTime.now());
        payment.setStripeCheckoutSessionId(checkoutSessionId);
        payment.setStripePaymentIntentId(paymentIntentId);
        paymentRepository.save(payment);

        // Point de passage unique de l'acompte ET du solde : les brancher ici, plutôt qu'à
        // chacun de leurs deux appelants, évite qu'un futur mode de règlement en réchappe.
        enrollmentPaymentService.emettreRecu(payment,
                payment.getInstallment() == PaymentInstallment.BALANCE
                        ? MotifPaiement.SOLDE_SCOLARITE
                        : MotifPaiement.ACOMPTE_SCOLARITE);
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
    private void appliquerEcheancier(EnrollmentDetailDto.EnrollmentDetailDtoBuilder builder,
                                     Enrollment enrollment) {
        try {
            var echeancier = enrollmentPaymentService.scheduleFor(enrollment);
            builder.tuitionDepositAmount(echeancier.depositAmount())
                    .tuitionBalanceAmount(echeancier.balanceAmount())
                    .tuitionDepositRate(echeancier.depositRate())
                    .tuitionOutstanding(enrollmentPaymentService.outstandingTuition(enrollment.getId()));
        } catch (RuntimeException e) {
            // Meme choix que pour le montant : un tarif absent ou invalide ne doit pas empecher
            // l'affichage du dossier, qui porte bien d'autres informations utiles.
            log.debug("Échéancier indisponible pour le dossier {} : {}",
                    enrollment.getId(), e.getMessage());
        }
    }

    private java.math.BigDecimal resolveTuitionAmountQuietly(Enrollment enrollment) {
        try {
            return enrollmentPaymentService.resolveTuitionAmount(enrollment);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * Retrait d'une candidature, par le medecin lui-meme ou par l'administration.
     *
     * <p>Le retrait n'est possible que tant que le dossier releve encore d'OptimiSante. Une
     * fois transmis au CHU, il engage un tiers qui l'examine : le faire disparaitre sous ses
     * yeux n'est pas un cas d'usage, et les etapes suivantes (acceptation, paiement, convention)
     * s'appuient dessus. Passe ce point, la sortie legitime est l'annulation motivee, qui
     * conserve la trace du dossier.</p>
     *
     * <p>La place reservee dans la session est rendue : sans cela, chaque retrait amputerait
     * definitivement la capacite de la session.</p>
     *
     * @param requesterId auteur de la demande ; ignore si {@code isAdmin}, sinon doit etre le
     *                    medecin proprietaire du dossier.
     */
    @Transactional
    public void deleteEnrollment(UUID enrollmentId, UUID requesterId, boolean isAdmin) {
        Enrollment enrollment = requireEnrollment(enrollmentId);

        if (!isAdmin && !enrollment.getDoctor().getId().equals(requesterId)) {
            throw new AccessDeniedException("Ce dossier ne vous appartient pas.");
        }

        if (!DOCTOR_EDITABLE_STATUSES.contains(enrollment.getStatus())) {
            throw new IllegalStateException(
                    "Ce dossier a deja ete transmis a l'etablissement : il ne peut plus etre "
                            + "supprime. Utilisez l'annulation, qui en conserve la trace.");
        }

        UUID sessionId = enrollment.getSession().getId();
        enrollmentRepository.delete(enrollment);
        // Force l'ordre : la suppression doit atteindre la base avant que la place ne soit
        // rendue, sinon le plafond de capacite refuserait l'increment.
        enrollmentRepository.flush();
        trainingSessionRepository.incrementAvailableSeats(sessionId);

        log.info("Dossier {} supprime par {} ({})", enrollmentId, requesterId,
                isAdmin ? "administration" : "le medecin");
    }

    /**
     * Nom affichable d'un medecin pour les notifications destinees a l'equipe admin.
     * Repli sur l'e-mail : un dossier tout juste cree peut ne pas encore avoir de profil.
     */
    private String doctorDisplayName(Enrollment enrollment) {
        User doctor = enrollment.getDoctor();
        return doctorProfileRepository.findByUserId(doctor.getId())
                .map(p -> "Dr. " + p.getFirstName() + " " + p.getLastName())
                .orElse(doctor.getEmail());
    }

    /**
     * Etats dans lesquels le dossier est encore entre les mains du medecin et d'OptimiSante :
     * il peut y etre complete, corrige, ou retire. Des qu'il part au CHU, il devient une piece
     * sur laquelle un tiers doit statuer, et se fige.
     */
    private static final java.util.Set<EnrollmentStatus> DOCTOR_EDITABLE_STATUSES =
            java.util.EnumSet.of(EnrollmentStatus.UNDER_OPTIMI_REVIEW, EnrollmentStatus.ACTION_REQUIRED);

    /**
     * Compte du partenaire d'accueil, destinataire de ses notifications. {@code null} si la
     * formation n'a pas de partenaire rattache : l'envoi est alors simplement ignore, plutot
     * que de faire echouer l'action metier pour une notification.
     */
    private UUID partnerUserId(Enrollment enrollment) {
        var partner = enrollment.getSession().getTraining().getPartnerProfile();
        return partner == null || partner.getUser() == null ? null : partner.getUser().getId();
    }

    /** Etablissement d'accueil de la session, pour situer le dossier dans les messages. */
    private String institutionName(Enrollment enrollment) {
        var partner = enrollment.getSession().getTraining().getPartnerProfile();
        return partner == null ? "l'etablissement d'accueil" : partner.getInstitutionName();
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

        // Le solde de formation conditionne le passage a « pret a demarrer ». Sans cette
        // garde, l'echeancier ne serait qu'une facilite de paiement sans echeance reelle : le
        // dossier irait au bout et l'etablissement accueillerait un candidat dont la formation
        // n'est payee qu'a 60 %. La transition reste inchangee dans l'automate — c'est une
        // condition prealable, pas un nouvel etat.
        if (newStatus == EnrollmentStatus.READY_TO_START) {
            java.math.BigDecimal restant = enrollmentPaymentService.outstandingTuition(enrollmentId);
            if (restant.signum() > 0) {
                throw new IllegalStateException(
                        "Le solde de formation n'est pas réglé (" + restant + " € restants). "
                        + "Le dossier ne peut pas passer à « prêt à démarrer ».");
            }
        }

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

        // ADMIN_MOBILITE doit figurer ici : depuis la scission des rôles (V30), c'est LUI qui
        // administre la mobilité. Son absence rendait ce contrôle plus strict que le
        // @PreAuthorize de l'endpoint, qui l'autorise — l'administrateur passait la porte puis
        // se voyait refuser à l'intérieur, avec « Unauthorized to upload documents ». Vérifié
        // sur la plateforme : le rôle censé constituer le dossier ne pouvait rien y déposer.
        com.optimisante.backend.domain.identity.entity.Role role = user.getRole();
        boolean isAdmin = role == com.optimisante.backend.domain.identity.entity.Role.ADMIN
                || role == com.optimisante.backend.domain.identity.entity.Role.SUPER_ADMIN
                || role == com.optimisante.backend.domain.identity.entity.Role.ADMIN_MOBILITE;
        if (!isAdmin && !enrollment.getDoctor().getId().equals(userId)) {
            throw new AccessDeniedException("Vous n'êtes pas autorisé à déposer une pièce sur ce dossier.");
        }

        // Une fois le dossier transmis au CHU, son contenu est fige pour le candidat : le
        // partenaire doit statuer sur les pieces exactes qui lui ont ete presentees.
        // L'administration conserve le droit d'ajouter des pieces (lettre consulaire,
        // attestation d'hebergement), qui relevent d'etapes posterieures.
        if (!isAdmin && !DOCTOR_EDITABLE_STATUSES.contains(enrollment.getStatus())) {
            throw new IllegalStateException(
                    "Votre dossier a ete transmis a l'etablissement : il n'est plus modifiable.");
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
        // Chargement joint plutôt que findAll() : le mapping traverse session → formation →
        // CHU partenaire pour chaque dossier. En chargement paresseux, cela déclenche trois
        // requêtes supplémentaires par ligne — invisible sur douze dossiers, intenable ensuite.
        List<Enrollment> dossiers = enrollmentRepository.findAllForAdmin();

        // Le nom du médecin vit dans doctor_profiles, hors du graphe des dossiers : la
        // jointure ci-dessus ne peut pas l'atteindre. Il est donc chargé en une fois, sans
        // quoi la liste refait une requête par ligne — mesuré, douze pour douze dossiers.
        Map<UUID, String> nomsMedecins = doctorProfileRepository.findByUserIdIn(
                        dossiers.stream().map(d -> d.getDoctor().getId()).collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(
                        profil -> profil.getUser().getId(),
                        profil -> "Dr. " + profil.getFirstName() + " " + profil.getLastName(),
                        (a, b) -> a));

        return dossiers.stream()
                .map(enrollment -> toDetailDto(enrollment, true, nomsMedecins))
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

        // Les pieces commerciales entre OptimiSante et le medecin sont ecartees : voir
        // DocumentType.isVisibleToPartner(). Le partenaire instruit un dossier de mobilite ;
        // il n'a a connaitre ni les achats du candidat aupres de l'agence, ni leurs tarifs.
        return enrollmentDocumentRepository.findByEnrollmentId(enrollmentId).stream()
                .filter(d -> d.getDocumentType().isVisibleToPartner())
                .toList();
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
        return toDetailDto(enrollment, includeDoctorName, null);
    }

    /**
     * @param nomsMedecins noms déjà chargés, indexés par identifiant d'utilisateur, ou
     *                     {@code null} pour un dossier isolé — auquel cas le nom est résolu à
     *                     la demande. Ce paramètre n'existe que pour les listes : y passer
     *                     {@code null} redonne le comportement d'origine.
     */
    private EnrollmentDetailDto toDetailDto(Enrollment enrollment, boolean includeDoctorName,
                                            Map<UUID, String> nomsMedecins) {
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
                .partnerProfileId(enrollment.getSession().getTraining().getPartnerProfile().getId())
                .partnerName(enrollment.getSession().getTraining().getPartnerProfile().getInstitutionName())
                .actionRequiredNote(enrollment.getActionRequiredNote())
                .rejectionReason(enrollment.getRejectionReason())
                // Montant résolu depuis la session (ou le tarif de la formation) : c'est ce
                // que le médecin devra régler, affiché avant même l'ouverture du paiement.
                .tuitionAmount(resolveTuitionAmountQuietly(enrollment));

        // L'echeancier n'est renseigne que lorsqu'un tarif existe : sur une formation dont le
        // prix n'est pas encore fixe, afficher « acompte de 0 € » serait plus trompeur que de
        // n'afficher aucun echeancier.
        appliquerEcheancier(builder, enrollment);

        if (includeDoctorName) {
            String doctorEmail = enrollment.getDoctor().getEmail();
            UUID doctorId = enrollment.getDoctor().getId();
            String doctorName = nomsMedecins != null
                    ? nomsMedecins.getOrDefault(doctorId, "Dr. " + doctorEmail)
                    : doctorProfileRepository.findByUserId(doctorId)
                            .map(p -> "Dr. " + p.getFirstName() + " " + p.getLastName())
                            .orElse("Dr. " + doctorEmail);
            builder.doctorName(doctorName).doctorEmail(doctorEmail);
        }

        return builder.build();
    }
}

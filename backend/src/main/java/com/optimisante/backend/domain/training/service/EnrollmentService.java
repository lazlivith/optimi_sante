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

@Slf4j
@Service
@RequiredArgsConstructor
public class EnrollmentService {

    private final EnrollmentRepository enrollmentRepository;
    private final TrainingSessionRepository trainingSessionRepository;
    private final UserRepository userRepository;
    private final DoctorProfileRepository doctorProfileRepository;
    private final EnrollmentDocumentRepository enrollmentDocumentRepository;
    private final com.optimisante.backend.domain.document.service.PdfGeneratorService pdfGeneratorService;
    private final com.optimisante.backend.common.storage.StorageService storageService;

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
        Enrollment enrollment = Enrollment.builder()
                .doctor(doctor)
                .session(session)
                .status(EnrollmentStatus.PENDING_REVIEW)
                .build();

        return toResponseDto(enrollmentRepository.save(enrollment));
    }

    @Transactional
    public EnrollmentResponseDto submitDocuments(UUID enrollmentId, UUID doctorId, DocumentUploadRequestDto dto) {
        Enrollment enrollment = enrollmentRepository.findByIdAndDoctorId(enrollmentId, doctorId)
                .orElseThrow(() -> new RuntimeException("Enrollment not found for this doctor"));

        if (enrollment.getStatus() != EnrollmentStatus.PENDING_REVIEW) {
            throw new IllegalStateException(
                    "Documents can only be submitted when status is PENDING_REVIEW");
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

        if (enrollment.getStatus() != EnrollmentStatus.APPROVED_ADMINISTRATIVE) {
            // Force status to APPROVED_ADMINISTRATIVE for the purpose of the flow if it's not already
            enrollment.setStatus(EnrollmentStatus.APPROVED_ADMINISTRATIVE);
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

    @Transactional
    public EnrollmentResponseDto updateEnrollmentStatus(UUID enrollmentId, EnrollmentStatus newStatus) {
        Enrollment enrollment = enrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new RuntimeException("Enrollment not found"));

        boolean wasNotAdministrative = enrollment.getStatus() != EnrollmentStatus.APPROVED_ADMINISTRATIVE;
        enrollment.setStatus(newStatus);

        Enrollment savedEnrollment = enrollmentRepository.save(enrollment);
        log.info("Enrollment {} status updated to {}", enrollmentId, newStatus);

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
        if (newStatus == EnrollmentStatus.APPROVED_ADMINISTRATIVE && wasNotAdministrative) {
            log.info("Déclenchement automatique de la génération de la convention tripartite pour l'inscription {}", enrollmentId);
            try {
                savedEnrollment = generateConventionInternal(enrollmentId);
            } catch (Exception e) {
                log.error("Statut de l'inscription {} mis à jour vers APPROVED_ADMINISTRATIVE, mais la génération " +
                        "automatique de la convention a échoué — à relancer manuellement.", enrollmentId, e);
            }

            // --- Génération automatique de l'Attestation d'Accueil / Inscription ---
            // Même déclencheur que la convention ("dès la validation du dossier" côté CDC), mais
            // dans un try/catch séparé et indépendant : un échec de l'un ne doit jamais empêcher
            // la génération de l'autre.
            log.info("Déclenchement automatique de la génération de l'attestation d'accueil pour l'inscription {}", enrollmentId);
            try {
                savedEnrollment = generateAttestationInternal(enrollmentId);
            } catch (Exception e) {
                log.error("Statut de l'inscription {} mis à jour vers APPROVED_ADMINISTRATIVE, mais la génération " +
                        "automatique de l'attestation d'accueil a échoué — à relancer manuellement.", enrollmentId, e);
            }
        }

        return toResponseDto(savedEnrollment);
    }

    @Transactional(readOnly = true)
    public List<Enrollment> getPartnerEnrollments(UUID partnerUserId) {
        return enrollmentRepository.findBySessionTrainingPartnerProfileUserId(partnerUserId);
    }

    @Transactional(readOnly = true)
    public List<Enrollment> getPartnerEnrollments(UUID partnerUserId, UUID trainingId) {
        if (trainingId == null) {
            return getPartnerEnrollments(partnerUserId);
        }
        return enrollmentRepository.findBySessionTrainingPartnerProfileUserIdAndSessionTrainingId(partnerUserId, trainingId);
    }

    @Transactional
    public Enrollment reviewAcademic(UUID enrollmentId, UUID partnerUserId, EnrollmentStatus status) {
        Enrollment enrollment = enrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new RuntimeException("Dossier introuvable"));

        // Vérifier que ce partenaire est bien propriétaire de la formation
        if (!enrollment.getSession().getTraining().getPartnerProfile().getUser().getId().equals(partnerUserId)) {
            throw new RuntimeException("Vous n'êtes pas autorisé à examiner ce dossier");
        }

        if (status != EnrollmentStatus.APPROVED_ACADEMIC && status != EnrollmentStatus.REJECTED) {
            throw new IllegalArgumentException("Statut invalide pour une révision académique");
        }

        enrollment.setStatus(status);
        log.info("Enrollment {} academic review status updated to {} by partner {}", enrollmentId, status, partnerUserId);
        return enrollmentRepository.save(enrollment);
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
                .attestationS3Key(enrollment.getAttestationS3Key());

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

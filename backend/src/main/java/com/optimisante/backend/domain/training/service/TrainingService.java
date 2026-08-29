package com.optimisante.backend.domain.training.service;

import com.optimisante.backend.common.storage.StorageService;
import com.optimisante.backend.config.tenant.TenantContext;
import com.optimisante.backend.domain.identity.entity.PartnerProfile;
import com.optimisante.backend.domain.identity.entity.Tenant;
import com.optimisante.backend.domain.identity.repository.PartnerProfileRepository;
import com.optimisante.backend.domain.identity.repository.TenantRepository;
import com.optimisante.backend.domain.training.dto.CreateTrainingRequestDto;
import com.optimisante.backend.domain.training.dto.LeadCaptureRequestDto;
import com.optimisante.backend.domain.training.dto.LeadCaptureResponseDto;
import com.optimisante.backend.domain.training.dto.CreateSessionRequestDto;
import com.optimisante.backend.domain.training.dto.PartnerTrainingResponseDto;
import com.optimisante.backend.domain.training.dto.TrainingSessionResponseDto;
import com.optimisante.backend.domain.training.dto.TrainingSummaryDto;
import com.optimisante.backend.domain.training.entity.ProspectLead;
import com.optimisante.backend.domain.training.entity.SessionStatus;
import com.optimisante.backend.domain.training.entity.Training;
import com.optimisante.backend.domain.training.entity.TrainingApprovalStatus;
import com.optimisante.backend.domain.training.entity.TrainingSession;
import com.optimisante.backend.domain.training.repository.ProspectLeadRepository;
import com.optimisante.backend.domain.training.repository.TrainingRepository;
import com.optimisante.backend.domain.training.repository.TrainingSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.text.Normalizer;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TrainingService {

    private final TrainingRepository trainingRepository;
    private final TrainingSessionRepository trainingSessionRepository;
    private final ProspectLeadRepository prospectLeadRepository;
    private final PartnerProfileRepository partnerProfileRepository;
    private final TenantRepository tenantRepository;
    private final StorageService storageService;

    @Transactional
    public LeadCaptureResponseDto captureLead(UUID trainingId, LeadCaptureRequestDto request) {
        Training training = trainingRepository.findById(trainingId)
                .orElseThrow(() -> new RuntimeException("Training not found with ID: " + trainingId));

        // Save the prospect lead
        ProspectLead lead = ProspectLead.builder()
                .training(training)
                .email(request.getEmail())
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .phoneWhatsapp(request.getPhoneWhatsapp())
                .country(request.getCountry())
                .specialty(request.getSpecialty())
                .source(request.getSource())
                .build();
        prospectLeadRepository.save(lead);

        // Generate download URL for brochure
        String downloadUrl = null;
        if (training.getBrochureS3Key() != null && !training.getBrochureS3Key().isBlank()) {
            try {
                // Generate a presigned URL valid for 60 minutes
                downloadUrl = storageService.generatePresignedOrSignedUrl(training.getBrochureS3Key(), 60);
            } catch (Exception e) {
                log.error("Failed to generate presigned URL for brochure {}: {}", training.getBrochureS3Key(), e.getMessage());
                // Optional: Fallback to a default brochure or generic page if Cloudinary fails
                downloadUrl = "https://optimisante.com/brochures/default-brochure.pdf";
            }
        } else {
            // Fallback if no brochure is linked
            downloadUrl = "https://optimisante.com/brochures/default-brochure.pdf";
        }

        return LeadCaptureResponseDto.builder()
                .brochureDownloadUrl(downloadUrl)
                .build();
    }

    @Transactional(readOnly = true)
    public List<TrainingSummaryDto> getPublishedTrainings() {
        return trainingRepository.findByIsPublishedTrue().stream()
                .map(training -> {
                    String location = trainingSessionRepository
                            .findByTrainingIdAndStatus(training.getId(), SessionStatus.OPEN)
                            .stream()
                            .findFirst()
                            .map(TrainingSession::getLocation)
                            .orElse(null);

                    return TrainingSummaryDto.builder()
                            .id(training.getId())
                            .title(training.getTitle())
                            .medicalSpecialty(training.getMedicalSpecialty())
                            .description(training.getDescription())
                            .durationDays(training.getDurationDays())
                            .isLongStay(training.getIsLongStay())
                            .location(location)
                            .brochureUrl(safeSignedUrl(training.getBrochureS3Key()))
                            .imageUrl(safeMediaUrl(training.getImageS3Key(), "image"))
                            .videoUrl(safeMediaUrl(training.getVideoS3Key(), "video"))
                            .price(training.getPrice())
                            .build();
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public TrainingSessionResponseDto createSession(CreateSessionRequestDto dto, UUID partnerUserId) {
        Training training = trainingRepository.findById(dto.trainingId())
                .orElseThrow(() -> new RuntimeException("Training not found"));

        if (!training.getPartnerProfile().getUser().getId().equals(partnerUserId)) {
            throw new IllegalStateException("Unauthorized: this training does not belong to your organization");
        }
        if (training.getApprovalStatus() != TrainingApprovalStatus.APPROVED) {
            throw new IllegalStateException(
                    "Cette formation doit d'abord être validée par l'administration avant d'ouvrir une session.");
        }

        TrainingSession session = TrainingSession.builder()
                .training(training)
                .startDate(dto.startDate())
                .endDate(dto.endDate())
                .capacity(dto.capacity())
                .availableSeats(dto.capacity())
                .location(dto.location())
                .price(dto.price())
                .status(SessionStatus.OPEN)
                .build();

        TrainingSession saved = trainingSessionRepository.save(session);

        return TrainingSessionResponseDto.builder()
                .id(saved.getId())
                .trainingId(training.getId())
                .trainingTitle(training.getTitle())
                .startDate(saved.getStartDate())
                .endDate(saved.getEndDate())
                .capacity(saved.getCapacity())
                .availableSeats(saved.getAvailableSeats())
                .location(saved.getLocation())
                .price(saved.getPrice())
                .status(saved.getStatus().name())
                .createdAt(saved.getCreatedAt())
                .build();
    }

    @Transactional(readOnly = true)
    public List<PartnerTrainingResponseDto> getMyTrainingsDetailed(UUID partnerUserId) {
        return trainingRepository.findByPartnerProfileUserId(partnerUserId).stream()
                .map(this::toPartnerResponseDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public PartnerTrainingResponseDto createTraining(CreateTrainingRequestDto dto, UUID partnerUserId) {
        PartnerProfile partnerProfile = partnerProfileRepository.findByUserId(partnerUserId)
                .orElseThrow(() -> new IllegalStateException("Profil partenaire introuvable"));
        Tenant tenant = tenantRepository.findById(TenantContext.getTenantId())
                .orElseThrow(() -> new RuntimeException("Tenant not found"));

        Training training = Training.builder()
                .tenant(tenant)
                .partnerProfile(partnerProfile)
                .title(dto.title())
                .slug(generateUniqueSlug(dto.title()))
                .medicalSpecialty(dto.medicalSpecialty())
                .description(dto.description())
                .durationDays(dto.durationDays())
                .isLongStay(dto.isLongStay())
                .price(dto.price())
                .isPublished(false)
                .approvalStatus(TrainingApprovalStatus.PENDING_REVIEW)
                .build();

        return toPartnerResponseDto(trainingRepository.save(training));
    }

    @Transactional
    public PartnerTrainingResponseDto updateTraining(UUID trainingId, CreateTrainingRequestDto dto, UUID partnerUserId) {
        Training training = requireOwnedTraining(trainingId, partnerUserId);

        training.setTitle(dto.title());
        training.setMedicalSpecialty(dto.medicalSpecialty());
        training.setDescription(dto.description());
        training.setDurationDays(dto.durationDays());
        training.setIsLongStay(dto.isLongStay());
        training.setPrice(dto.price());
        // Toute modification doit repasser par une validation admin avant de rester visible du public.
        training.setApprovalStatus(TrainingApprovalStatus.PENDING_REVIEW);
        training.setIsPublished(false);
        training.setRejectionReason(null);

        return toPartnerResponseDto(trainingRepository.save(training));
    }

    @Transactional
    public void deleteTraining(UUID trainingId, UUID partnerUserId) {
        Training training = requireOwnedTraining(trainingId, partnerUserId);
        if (trainingSessionRepository.existsByTrainingId(trainingId)) {
            throw new IllegalStateException(
                    "Impossible de supprimer une formation qui a déjà des sessions programmées.");
        }
        trainingRepository.delete(training);
    }

    @Transactional
    public PartnerTrainingResponseDto uploadTrainingImage(UUID trainingId, MultipartFile file, UUID partnerUserId) {
        Training training = requireOwnedTraining(trainingId, partnerUserId);
        String publicId = storageService.uploadMedia(file, "trainings/images", "image");
        training.setImageS3Key(publicId);
        return toPartnerResponseDto(trainingRepository.save(training));
    }

    @Transactional
    public PartnerTrainingResponseDto uploadTrainingVideo(UUID trainingId, MultipartFile file, UUID partnerUserId) {
        Training training = requireOwnedTraining(trainingId, partnerUserId);
        String publicId = storageService.uploadMedia(file, "trainings/videos", "video");
        training.setVideoS3Key(publicId);
        return toPartnerResponseDto(trainingRepository.save(training));
    }

    private Training requireOwnedTraining(UUID trainingId, UUID partnerUserId) {
        Training training = trainingRepository.findById(trainingId)
                .orElseThrow(() -> new RuntimeException("Training not found"));
        if (!training.getPartnerProfile().getUser().getId().equals(partnerUserId)) {
            throw new IllegalStateException("Cette formation n'appartient pas à votre établissement");
        }
        return training;
    }

    private PartnerTrainingResponseDto toPartnerResponseDto(Training training) {
        return PartnerTrainingResponseDto.builder()
                .id(training.getId())
                .title(training.getTitle())
                .slug(training.getSlug())
                .medicalSpecialty(training.getMedicalSpecialty())
                .description(training.getDescription())
                .durationDays(training.getDurationDays())
                .isLongStay(training.getIsLongStay())
                .price(training.getPrice())
                .isPublished(training.getIsPublished())
                .approvalStatus(training.getApprovalStatus().name())
                .rejectionReason(training.getRejectionReason())
                .brochureUrl(safeSignedUrl(training.getBrochureS3Key()))
                .imageUrl(safeMediaUrl(training.getImageS3Key(), "image"))
                .videoUrl(safeMediaUrl(training.getVideoS3Key(), "video"))
                .createdAt(training.getCreatedAt())
                .build();
    }

    private String safeSignedUrl(String publicId) {
        if (publicId == null || publicId.isBlank()) return null;
        try {
            return storageService.generatePresignedOrSignedUrl(publicId, 60);
        } catch (Exception e) {
            log.error("Failed to generate signed URL for {}: {}", publicId, e.getMessage());
            return null;
        }
    }

    private String safeMediaUrl(String publicId, String resourceType) {
        if (publicId == null || publicId.isBlank()) return null;
        try {
            return storageService.generateMediaUrl(publicId, resourceType);
        } catch (Exception e) {
            log.error("Failed to generate media URL for {}: {}", publicId, e.getMessage());
            return null;
        }
    }

    private String generateUniqueSlug(String title) {
        String base = slugify(title);
        String slug = base;
        int suffix = 2;
        while (trainingRepository.findBySlug(slug).isPresent()) {
            slug = base + "-" + suffix;
            suffix++;
        }
        return slug;
    }

    private String slugify(String input) {
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        String slug = normalized.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        return slug.isBlank() ? "formation" : slug;
    }

    @Transactional(readOnly = true)
    public List<TrainingSessionResponseDto> getMySessions(UUID partnerUserId) {
        return trainingSessionRepository.findByTrainingPartnerProfileUserId(partnerUserId).stream()
                .map(session -> TrainingSessionResponseDto.builder()
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
                        .build())
                .collect(Collectors.toList());
    }
}

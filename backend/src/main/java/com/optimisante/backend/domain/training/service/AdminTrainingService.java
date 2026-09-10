package com.optimisante.backend.domain.training.service;

import com.optimisante.backend.common.storage.StorageService;
import com.optimisante.backend.domain.training.dto.AdminTrainingResponseDto;
import com.optimisante.backend.domain.training.entity.Training;
import com.optimisante.backend.domain.training.entity.TrainingApprovalStatus;
import com.optimisante.backend.domain.training.repository.TrainingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminTrainingService {

    private final TrainingRepository trainingRepository;
    private final StorageService storageService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public List<AdminTrainingResponseDto> listTrainings(TrainingApprovalStatus statusFilter) {
        List<Training> trainings = statusFilter != null
                ? trainingRepository.findByApprovalStatus(statusFilter)
                : trainingRepository.findAll();

        return trainings.stream()
                .sorted(Comparator.comparing(Training::getCreatedAt).reversed())
                .map(this::toResponseDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public AdminTrainingResponseDto approve(UUID trainingId) {
        Training training = trainingRepository.findById(trainingId)
                .orElseThrow(() -> new RuntimeException("Training not found"));
        training.setApprovalStatus(TrainingApprovalStatus.APPROVED);
        training.setIsPublished(true);
        training.setRejectionReason(null);
        Training saved = trainingRepository.save(training);

        // Sans cette notification, un CHU ne savait pas si son offre etait publiee : il lui
        // fallait revenir la consulter au hasard dans son espace.
        eventPublisher.publishEvent(new com.optimisante.backend.domain.notification.event.NotificationEvents.TrainingApprovalDecided(
                saved.getId(), saved.getPartnerProfile().getUser().getId(), saved.getTitle(),
                true, null));

        return toResponseDto(saved);
    }

    @Transactional
    public AdminTrainingResponseDto reject(UUID trainingId, String reason) {
        Training training = trainingRepository.findById(trainingId)
                .orElseThrow(() -> new RuntimeException("Training not found"));
        training.setApprovalStatus(TrainingApprovalStatus.REJECTED);
        training.setIsPublished(false);
        training.setRejectionReason(reason);
        Training saved = trainingRepository.save(training);

        eventPublisher.publishEvent(new com.optimisante.backend.domain.notification.event.NotificationEvents.TrainingApprovalDecided(
                saved.getId(), saved.getPartnerProfile().getUser().getId(), saved.getTitle(),
                false, reason));

        return toResponseDto(saved);
    }

    private AdminTrainingResponseDto toResponseDto(Training training) {
        return AdminTrainingResponseDto.builder()
                .id(training.getId())
                .title(training.getTitle())
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
                .partnerInstitutionName(training.getPartnerProfile().getInstitutionName())
                .partnerContactEmail(training.getPartnerProfile().getContactEmail())
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
}

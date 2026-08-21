package com.optimisante.backend.domain.training.service;

import com.optimisante.backend.common.storage.StorageService;
import com.optimisante.backend.domain.training.dto.LeadCaptureRequestDto;
import com.optimisante.backend.domain.training.dto.LeadCaptureResponseDto;
import com.optimisante.backend.domain.training.entity.ProspectLead;
import com.optimisante.backend.domain.training.entity.Training;
import com.optimisante.backend.domain.training.repository.ProspectLeadRepository;
import com.optimisante.backend.domain.training.repository.TrainingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TrainingService {

    private final TrainingRepository trainingRepository;
    private final ProspectLeadRepository prospectLeadRepository;
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
}

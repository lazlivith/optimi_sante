package com.optimisante.backend.domain.training.controller;

import com.optimisante.backend.common.storage.StorageService;
import com.optimisante.backend.domain.training.dto.CreateTrainingRequestDto;
import com.optimisante.backend.domain.training.dto.PartnerTrainingResponseDto;
import com.optimisante.backend.domain.training.entity.Training;
import com.optimisante.backend.domain.training.repository.TrainingRepository;
import com.optimisante.backend.domain.training.service.TrainingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/partner/trainings")
@RequiredArgsConstructor
public class PartnerTrainingResource {

    private final StorageService storageService;
    private final TrainingRepository trainingRepository;
    private final TrainingService trainingService;

    @GetMapping
    @PreAuthorize("hasRole('CENTRE_FORMATION')")
    public ResponseEntity<List<PartnerTrainingResponseDto>> getMyTrainings(Authentication auth) {
        UUID partnerUserId = UUID.fromString(auth.getPrincipal().toString());
        return ResponseEntity.ok(trainingService.getMyTrainingsDetailed(partnerUserId));
    }

    @PostMapping
    @PreAuthorize("hasRole('CENTRE_FORMATION')")
    public ResponseEntity<PartnerTrainingResponseDto> createTraining(
            @Valid @RequestBody CreateTrainingRequestDto dto, Authentication auth) {
        UUID partnerUserId = UUID.fromString(auth.getPrincipal().toString());
        return ResponseEntity.ok(trainingService.createTraining(dto, partnerUserId));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('CENTRE_FORMATION')")
    public ResponseEntity<PartnerTrainingResponseDto> updateTraining(
            @PathVariable UUID id, @Valid @RequestBody CreateTrainingRequestDto dto, Authentication auth) {
        UUID partnerUserId = UUID.fromString(auth.getPrincipal().toString());
        return ResponseEntity.ok(trainingService.updateTraining(id, dto, partnerUserId));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('CENTRE_FORMATION')")
    public ResponseEntity<Void> deleteTraining(@PathVariable UUID id, Authentication auth) {
        UUID partnerUserId = UUID.fromString(auth.getPrincipal().toString());
        trainingService.deleteTraining(id, partnerUserId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/image")
    @PreAuthorize("hasRole('CENTRE_FORMATION')")
    public ResponseEntity<PartnerTrainingResponseDto> uploadImage(
            @PathVariable UUID id, @RequestParam("file") MultipartFile file, Authentication auth) {
        UUID partnerUserId = UUID.fromString(auth.getPrincipal().toString());
        return ResponseEntity.ok(trainingService.uploadTrainingImage(id, file, partnerUserId));
    }

    @PostMapping("/{id}/video")
    @PreAuthorize("hasRole('CENTRE_FORMATION')")
    public ResponseEntity<PartnerTrainingResponseDto> uploadVideo(
            @PathVariable UUID id, @RequestParam("file") MultipartFile file, Authentication auth) {
        UUID partnerUserId = UUID.fromString(auth.getPrincipal().toString());
        return ResponseEntity.ok(trainingService.uploadTrainingVideo(id, file, partnerUserId));
    }

    @PostMapping("/{id}/brochure")
    @PreAuthorize("hasRole('CENTRE_FORMATION') or hasRole('SUPER_ADMIN') or hasRole('ADMIN')")
    @Transactional
    public ResponseEntity<Map<String, String>> uploadBrochure(
            @PathVariable UUID id,
            @RequestParam("file") MultipartFile file,
            Authentication auth) {

        Training training = trainingRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Training not found"));

        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_SUPER_ADMIN"));
        if (!isAdmin) {
            UUID partnerUserId = UUID.fromString(auth.getPrincipal().toString());
            if (!training.getPartnerProfile().getUser().getId().equals(partnerUserId)) {
                return ResponseEntity.status(403).build();
            }
        }

        String publicId = storageService.uploadFile(file, "docs/brochures");
        training.setBrochureS3Key(publicId);
        trainingRepository.save(training);

        return ResponseEntity.ok(Map.of("publicId", publicId, "message", "Brochure rattachée à la formation avec succès"));
    }
}

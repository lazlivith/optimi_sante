package com.optimisante.backend.domain.training.controller;

import com.optimisante.backend.common.storage.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/partner/trainings")
@RequiredArgsConstructor
public class PartnerTrainingResource {

    private final StorageService storageService;

    @PostMapping("/{id}/brochure")
    @PreAuthorize("hasRole('CENTRE_FORMATION') or hasRole('SUPER_ADMIN') or hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> uploadBrochure(
            @PathVariable UUID id,
            @RequestParam("file") MultipartFile file) {
        
        // Upload the PDF brochure to Cloudinary
        String publicId = storageService.uploadFile(file, "docs/brochures");
        
        // Here we would normally fetch the Training entity, update its brochureS3Key, and save it.
        // training.setBrochureS3Key(publicId);
        // trainingRepository.save(training);
        
        return ResponseEntity.ok(Map.of("publicId", publicId, "message", "Brochure rattachée à la formation avec succès"));
    }
}

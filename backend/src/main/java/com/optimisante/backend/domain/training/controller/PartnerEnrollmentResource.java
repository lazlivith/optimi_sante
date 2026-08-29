package com.optimisante.backend.domain.training.controller;

import com.optimisante.backend.domain.document.dto.DocumentItemDto;
import com.optimisante.backend.domain.identity.repository.DoctorProfileRepository;
import com.optimisante.backend.domain.training.entity.Enrollment;
import com.optimisante.backend.domain.training.entity.EnrollmentStatus;
import com.optimisante.backend.domain.training.service.EnrollmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/partner/enrollments")
@RequiredArgsConstructor
public class PartnerEnrollmentResource {

    private final EnrollmentService enrollmentService;
    private final DoctorProfileRepository doctorProfileRepository;

    @GetMapping
    @PreAuthorize("hasRole('CENTRE_FORMATION') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> getPartnerEnrollments(
            Authentication auth,
            @RequestParam(required = false) UUID trainingId) {
        UUID partnerUserId = UUID.fromString(auth.getPrincipal().toString());
        List<Enrollment> enrollments = enrollmentService.getPartnerEnrollments(partnerUserId, trainingId);

        // Map to a simple DTO structure that matches the frontend EnrollmentDto
        List<Map<String, Object>> response = enrollments.stream().map(e -> Map.<String, Object>of(
                "id", e.getId(),
                "doctorEmail", e.getDoctor().getEmail(),
                "doctorName", doctorProfileRepository.findByUserId(e.getDoctor().getId())
                        .map(p -> "Dr. " + p.getFirstName() + " " + p.getLastName())
                        .orElse("Dr. " + e.getDoctor().getEmail()),
                "sessionStartDate", e.getSession().getStartDate().toString(),
                "status", e.getStatus().name(),
                "submittedAt", e.getSubmittedAt().toString(),
                "trainingTitle", e.getSession().getTraining().getTitle(),
                "trainingId", e.getSession().getTraining().getId()
        )).collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/documents")
    @PreAuthorize("hasRole('CENTRE_FORMATION') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<List<DocumentItemDto>> getEnrollmentDocuments(
            @PathVariable UUID id, Authentication auth) {
        UUID partnerUserId = UUID.fromString(auth.getPrincipal().toString());
        List<DocumentItemDto> documents = enrollmentService.getEnrollmentDocumentsForPartner(id, partnerUserId).stream()
                .map(doc -> DocumentItemDto.builder()
                        .id(doc.getId())
                        .title(doc.getDocumentType().name())
                        .type(doc.getDocumentType().name())
                        .date(doc.getUploadedAt().atOffset(java.time.ZoneOffset.UTC))
                        .status(Boolean.TRUE.equals(doc.getIsVerified()) ? "VERIFIED" : "PENDING")
                        .documentKey(doc.getCloudinaryPublicId())
                        .build())
                .collect(Collectors.toList());
        return ResponseEntity.ok(documents);
    }

    @PatchMapping("/{id}/academic-review")
    @PreAuthorize("hasRole('CENTRE_FORMATION') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<Map<String, String>> reviewAcademic(
            @PathVariable UUID id,
            @RequestBody Map<String, String> request,
            Authentication auth) {
        UUID partnerUserId = UUID.fromString(auth.getPrincipal().toString());
        
        String statusStr = request.get("status");
        if (statusStr == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Status is required"));
        }
        
        EnrollmentStatus status;
        try {
            status = EnrollmentStatus.valueOf(statusStr);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", "Invalid status value"));
        }

        enrollmentService.reviewAcademic(id, partnerUserId, status);
        return ResponseEntity.ok(Map.of("message", "Academic review updated successfully"));
    }
}

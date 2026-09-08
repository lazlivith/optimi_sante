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
import com.optimisante.backend.domain.training.dto.EnrollmentResponseDto;

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

    /**
     * Décision pédagogique du partenaire : accepte ou refuse la candidature.
     * Une acceptation bascule automatiquement le dossier en attente de paiement.
     */
    /**
     * Demande de pièce complémentaire : alternative au refus quand le dossier est
     * incomplet plutôt qu'irrecevable. Renvoie le dossier à OptimiSanté.
     */
    @PostMapping("/{id}/request-correction")
    @PreAuthorize("hasRole('CENTRE_FORMATION')")
    public ResponseEntity<EnrollmentResponseDto> requestCorrection(@PathVariable UUID id,
                                                                   @RequestBody Map<String, String> body,
                                                                   Authentication auth) {
        UUID partnerId = UUID.fromString(auth.getPrincipal().toString());
        return ResponseEntity.ok(
                enrollmentService.requestPartnerCorrection(id, partnerId, body.get("note")));
    }

    @PostMapping("/{id}/decision")
    @PreAuthorize("hasRole('CENTRE_FORMATION')")
    public ResponseEntity<EnrollmentResponseDto> decide(@PathVariable UUID id,
                                                        @RequestBody Map<String, Object> body,
                                                        Authentication auth) {
        UUID partnerId = UUID.fromString(auth.getPrincipal().toString());
        boolean accept = Boolean.TRUE.equals(body.get("accept"));
        String reason = body.get("reason") != null ? String.valueOf(body.get("reason")) : null;
        return ResponseEntity.ok(enrollmentService.partnerDecision(id, partnerId, accept, reason));
    }

}

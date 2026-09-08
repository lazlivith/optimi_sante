package com.optimisante.backend.domain.training.controller;

import com.optimisante.backend.domain.document.dto.DocumentItemDto;
import com.optimisante.backend.domain.training.dto.EnrollmentDetailDto;
import com.optimisante.backend.domain.training.dto.EnrollmentResponseDto;
import com.optimisante.backend.domain.training.service.EnrollmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import com.optimisante.backend.domain.training.entity.EnrollmentStatus;
import org.springframework.security.core.Authentication;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/enrollments")
@RequiredArgsConstructor
public class AdminEnrollmentResource {

    private final EnrollmentService enrollmentService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<List<EnrollmentDetailDto>> listEnrollments() {
        return ResponseEntity.ok(enrollmentService.getAllEnrollmentsForAdmin());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<EnrollmentDetailDto> getEnrollment(@PathVariable UUID id) {
        return ResponseEntity.ok(enrollmentService.getEnrollmentDetailForAdmin(id));
    }

    @GetMapping("/{id}/documents")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<List<DocumentItemDto>> getEnrollmentDocuments(@PathVariable UUID id) {
        List<DocumentItemDto> documents = enrollmentService.getEnrollmentDocuments(id).stream()
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

    @PostMapping("/{id}/generate-convention")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<EnrollmentResponseDto> generateConvention(@PathVariable UUID id) {
        return ResponseEntity.ok(enrollmentService.generateConvention(id));
    }

    @PostMapping("/{id}/generate-attestation")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<EnrollmentResponseDto> generateAttestation(@PathVariable UUID id) {
        return ResponseEntity.ok(enrollmentService.generateAttestation(id));
    }

    /**
     * Pré-qualification OptimiSanté : les pièces sont vérifiées, le dossier part au CHU.
     * Tant que cet appel n'a pas eu lieu, le partenaire ne voit pas le dossier.
     */
    @PostMapping("/{id}/submit-to-partner")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<EnrollmentResponseDto> submitToPartner(@PathVariable UUID id,
                                                                 Authentication auth) {
        UUID adminId = UUID.fromString(auth.getPrincipal().toString());
        return ResponseEntity.ok(enrollmentService.submitToPartner(id, adminId));
    }

    /** Demande de pièces complémentaires : la main repasse au médecin, motif obligatoire. */
    @PostMapping("/{id}/request-action")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<EnrollmentResponseDto> requestAction(@PathVariable UUID id,
                                                               @RequestBody Map<String, String> body,
                                                               Authentication auth) {
        UUID adminId = UUID.fromString(auth.getPrincipal().toString());
        return ResponseEntity.ok(enrollmentService.requestAction(id, adminId, body.get("note")));
    }

    /** Avancement du suivi de mobilité (convention, visa, départ). */
    @PatchMapping("/{id}/mobility")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<EnrollmentResponseDto> advanceMobility(@PathVariable UUID id,
                                                                 @RequestParam EnrollmentStatus status) {
        return ResponseEntity.ok(enrollmentService.advanceMobility(id, status));
    }

    /** Annulation administrative, motif obligatoire. */
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<EnrollmentResponseDto> cancel(@PathVariable UUID id,
                                                        @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(enrollmentService.cancelEnrollment(id, body.get("reason")));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<EnrollmentResponseDto> updateEnrollmentStatus(
            @PathVariable UUID id,
            @RequestParam("status") com.optimisante.backend.domain.training.entity.EnrollmentStatus status) {
        return ResponseEntity.ok(enrollmentService.updateEnrollmentStatus(id, status));
    }
}

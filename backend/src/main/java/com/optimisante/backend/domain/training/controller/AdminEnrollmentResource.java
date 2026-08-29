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

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<EnrollmentResponseDto> updateEnrollmentStatus(
            @PathVariable UUID id,
            @RequestParam("status") com.optimisante.backend.domain.training.entity.EnrollmentStatus status) {
        return ResponseEntity.ok(enrollmentService.updateEnrollmentStatus(id, status));
    }
}

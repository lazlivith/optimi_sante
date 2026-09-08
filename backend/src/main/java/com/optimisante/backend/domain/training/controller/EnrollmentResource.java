package com.optimisante.backend.domain.training.controller;

import com.optimisante.backend.domain.training.dto.DocumentUploadRequestDto;
import com.optimisante.backend.domain.training.dto.EnrollmentDetailDto;
import com.optimisante.backend.domain.training.dto.EnrollmentDocumentResponseDto;
import com.optimisante.backend.domain.training.dto.EnrollmentRequestDto;
import com.optimisante.backend.domain.training.dto.EnrollmentResponseDto;
import com.optimisante.backend.domain.training.dto.TrainingSessionResponseDto;
import com.optimisante.backend.domain.training.entity.DocumentType;
import com.optimisante.backend.domain.training.service.EnrollmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;
import com.optimisante.backend.domain.training.finance.TuitionPaymentService;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class EnrollmentResource {

    private final EnrollmentService enrollmentService;
    private final com.optimisante.backend.domain.training.finance.TuitionPaymentService tuitionPaymentService;

    @GetMapping("/trainings/{trainingId}/sessions")
    public ResponseEntity<List<TrainingSessionResponseDto>> getAvailableSessions(@PathVariable UUID trainingId) {
        return ResponseEntity.ok(enrollmentService.getAvailableSessions(trainingId));
    }

    @GetMapping("/enrollments")
    @PreAuthorize("hasRole('MEDECIN')")
    public ResponseEntity<List<EnrollmentDetailDto>> getMyEnrollments(Authentication auth) {
        UUID doctorId = UUID.fromString(auth.getPrincipal().toString());
        return ResponseEntity.ok(enrollmentService.getMyEnrollments(doctorId));
    }

    @GetMapping("/enrollments/{id}")
    @PreAuthorize("hasRole('MEDECIN')")
    public ResponseEntity<EnrollmentDetailDto> getMyEnrollment(
            @PathVariable UUID id,
            Authentication auth) {
        UUID doctorId = UUID.fromString(auth.getPrincipal().toString());
        return ResponseEntity.ok(enrollmentService.getEnrollmentDetailForDoctor(id, doctorId));
    }

    @PostMapping("/enrollments")
    @PreAuthorize("hasRole('MEDECIN')")
    public ResponseEntity<EnrollmentResponseDto> createEnrollment(
            @Valid @RequestBody EnrollmentRequestDto request,
            Authentication auth) {
        UUID doctorId = UUID.fromString(auth.getPrincipal().toString());
        return ResponseEntity.ok(enrollmentService.createEnrollment(request, doctorId));
    }

    /**
     * Ouvre le paiement des frais de formation. Accessible uniquement lorsque le dossier
     * a été accepté par l'établissement et attend le règlement.
     */
    @PostMapping("/enrollments/{id}/tuition-checkout")
    @PreAuthorize("hasRole('MEDECIN')")
    public ResponseEntity<TuitionPaymentService.TuitionCheckoutDto> createTuitionCheckout(
            @PathVariable UUID id, Authentication auth) {
        UUID doctorId = UUID.fromString(auth.getPrincipal().toString());
        return ResponseEntity.ok(tuitionPaymentService.createTuitionCheckoutSession(id, doctorId));
    }

    /** Le médecin resoumet son dossier après avoir déposé les pièces demandées. */
    @PostMapping("/enrollments/{id}/resubmit")
    @PreAuthorize("hasRole('MEDECIN')")
    public ResponseEntity<EnrollmentResponseDto> resubmitAfterAction(@PathVariable UUID id,
                                                                     Authentication auth) {
        UUID doctorId = UUID.fromString(auth.getPrincipal().toString());
        return ResponseEntity.ok(enrollmentService.resubmitAfterAction(id, doctorId));
    }

    @PutMapping("/enrollments/{id}/documents")
    @PreAuthorize("hasRole('MEDECIN')")
    public ResponseEntity<EnrollmentResponseDto> submitDocuments(
            @PathVariable UUID id,
            @Valid @RequestBody DocumentUploadRequestDto request,
            Authentication auth) {
        UUID doctorId = UUID.fromString(auth.getPrincipal().toString());
        return ResponseEntity.ok(enrollmentService.submitDocuments(id, doctorId, request));
    }

    @PostMapping("/enrollments/{id}/documents")
    @PreAuthorize("hasRole('MEDECIN') or hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<EnrollmentDocumentResponseDto> uploadEnrollmentDocument(
            @PathVariable UUID id,
            @RequestParam("file") MultipartFile file,
            @RequestParam("documentType") DocumentType documentType,
            Authentication auth) {
        UUID userId = UUID.fromString(auth.getPrincipal().toString());
        return ResponseEntity.ok(enrollmentService.uploadEnrollmentDocument(id, userId, file, documentType));
    }
}

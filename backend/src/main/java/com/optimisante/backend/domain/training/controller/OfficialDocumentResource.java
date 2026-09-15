package com.optimisante.backend.domain.training.controller;

import com.optimisante.backend.config.security.MobilityAdmin;
import com.optimisante.backend.domain.training.dto.OfficialDocumentDtos.OfficialDocumentView;
import com.optimisante.backend.domain.training.dto.OfficialDocumentDtos.ReviewRequest;
import com.optimisante.backend.domain.training.entity.OfficialDocumentCategory;
import com.optimisante.backend.domain.training.service.OfficialDocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/**
 * Documents officiels d'un dossier : le CHU dépose, Optimi Santé vérifie et complète.
 *
 * <p>Un préfixe par rôle, comme les entretiens : aucune route ne permet au CHU de s'adresser au
 * médecin, ni de voir le kit de départ. Le médecin, lui, lit ces documents par son coffre-fort
 * ({@code /doctor/vault/dossiers}).</p>
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class OfficialDocumentResource {

    private final OfficialDocumentService service;

    // ---------------------------------------------------------------------- PARTENAIRE ----

    @GetMapping("/partner/enrollments/{enrollmentId}/official-documents")
    @PreAuthorize("hasRole('CENTRE_FORMATION')")
    public ResponseEntity<List<OfficialDocumentView>> listForPartner(@PathVariable UUID enrollmentId,
                                                                     Authentication auth) {
        return ResponseEntity.ok(service.pourPartenaire(enrollmentId, userId(auth)));
    }

    @PostMapping("/partner/enrollments/{enrollmentId}/official-documents")
    @PreAuthorize("hasRole('CENTRE_FORMATION')")
    public ResponseEntity<OfficialDocumentView> uploadByPartner(
            @PathVariable UUID enrollmentId,
            @RequestParam("file") MultipartFile file,
            @RequestParam("category") OfficialDocumentCategory category,
            @RequestParam(value = "title", required = false) String title,
            Authentication auth) {
        return ResponseEntity.ok(service.deposerParPartenaire(enrollmentId, userId(auth), file, category, title));
    }

    @DeleteMapping("/partner/official-documents/{documentId}")
    @PreAuthorize("hasRole('CENTRE_FORMATION')")
    public ResponseEntity<Void> deleteByPartner(@PathVariable UUID documentId, Authentication auth) {
        service.retirer(documentId, userId(auth), false);
        return ResponseEntity.noContent().build();
    }

    // -------------------------------------------------------------------------- ADMIN ----

    @GetMapping("/admin/enrollments/{enrollmentId}/official-documents")
    @MobilityAdmin
    public ResponseEntity<List<OfficialDocumentView>> listForAdmin(@PathVariable UUID enrollmentId) {
        return ResponseEntity.ok(service.pourAdmin(enrollmentId));
    }

    @PostMapping("/admin/enrollments/{enrollmentId}/official-documents")
    @MobilityAdmin
    public ResponseEntity<OfficialDocumentView> uploadByAdmin(
            @PathVariable UUID enrollmentId,
            @RequestParam("file") MultipartFile file,
            @RequestParam("category") OfficialDocumentCategory category,
            @RequestParam(value = "title", required = false) String title,
            Authentication auth) {
        return ResponseEntity.ok(service.deposerParAdmin(enrollmentId, userId(auth), file, category, title));
    }

    @PostMapping("/admin/official-documents/{documentId}/review")
    @MobilityAdmin
    public ResponseEntity<OfficialDocumentView> review(@PathVariable UUID documentId,
                                                       @RequestBody ReviewRequest body,
                                                       Authentication auth) {
        return ResponseEntity.ok(service.verifier(documentId, userId(auth),
                Boolean.TRUE.equals(body.accept()), body.reason()));
    }

    @DeleteMapping("/admin/official-documents/{documentId}")
    @MobilityAdmin
    public ResponseEntity<Void> deleteByAdmin(@PathVariable UUID documentId, Authentication auth) {
        service.retirer(documentId, userId(auth), true);
        return ResponseEntity.noContent().build();
    }

    private static UUID userId(Authentication auth) {
        return UUID.fromString(auth.getPrincipal().toString());
    }
}

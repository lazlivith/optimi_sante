package com.optimisante.backend.domain.training.controller;

import com.optimisante.backend.config.security.MobilityAdmin;
import com.optimisante.backend.domain.training.dto.DocumentRequestDtos.CreateRequest;
import com.optimisante.backend.domain.training.dto.DocumentRequestDtos.DossierSummary;
import com.optimisante.backend.domain.training.dto.DocumentRequestDtos.RequestView;
import com.optimisante.backend.domain.training.dto.DocumentRequestDtos.ReviewRequest;
import com.optimisante.backend.domain.training.service.EnrollmentDocumentRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * Espace « pièces du dossier » : l'administration réclame, le médecin dépose, l'administration
 * vérifie.
 *
 * <p>Les deux publics sont servis par le même contrôleur mais par des routes distinctes, sous
 * des préfixes qui portent déjà leurs droits : {@code /admin/…} pour l'administration de la
 * mobilité, {@code /enrollments/…} pour le médecin propriétaire du dossier. Le service
 * revérifie la propriété du dossier côté médecin — l'URL ne prouve rien.</p>
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class EnrollmentDocumentRequestResource {

    private final EnrollmentDocumentRequestService service;

    // -------------------------------------------------------------------- Administration --

    @GetMapping("/admin/enrollments/{enrollmentId}/document-requests")
    @MobilityAdmin
    public ResponseEntity<DossierSummary> listForAdmin(@PathVariable UUID enrollmentId) {
        return ResponseEntity.ok(service.getDossierForAdmin(enrollmentId));
    }

    @PostMapping("/admin/enrollments/{enrollmentId}/document-requests")
    @MobilityAdmin
    public ResponseEntity<RequestView> requestDocument(@PathVariable UUID enrollmentId,
                                                       @Valid @RequestBody CreateRequest body,
                                                       Authentication auth) {
        UUID adminId = UUID.fromString(auth.getPrincipal().toString());
        return ResponseEntity.ok(service.requestDocument(enrollmentId, adminId, body));
    }

    @PostMapping("/admin/document-requests/{requestId}/review")
    @MobilityAdmin
    public ResponseEntity<RequestView> review(@PathVariable UUID requestId,
                                              @Valid @RequestBody ReviewRequest body,
                                              Authentication auth) {
        UUID adminId = UUID.fromString(auth.getPrincipal().toString());
        return ResponseEntity.ok(service.reviewRequest(requestId, adminId, body));
    }

    /** Retire une demande. Volontairement un POST et non un DELETE : la ligne n'est pas
     *  supprimée mais passée en CANCELLED, pour que l'historique reste lisible. */
    @PostMapping("/admin/document-requests/{requestId}/cancel")
    @MobilityAdmin
    public ResponseEntity<RequestView> cancel(@PathVariable UUID requestId, Authentication auth) {
        UUID adminId = UUID.fromString(auth.getPrincipal().toString());
        return ResponseEntity.ok(service.cancelRequest(requestId, adminId));
    }

    // -------------------------------------------------------------------------- Médecin ---

    @GetMapping("/enrollments/{enrollmentId}/document-requests")
    @PreAuthorize("hasRole('MEDECIN')")
    public ResponseEntity<DossierSummary> listForDoctor(@PathVariable UUID enrollmentId,
                                                        Authentication auth) {
        UUID doctorId = UUID.fromString(auth.getPrincipal().toString());
        return ResponseEntity.ok(service.getDossierForDoctor(enrollmentId, doctorId));
    }

    /**
     * Dépôt de la pièce réclamée.
     *
     * <p>Route distincte de l'envoi générique de document : c'est elle qui rattache le fichier
     * à la demande. Déposée ailleurs, la pièce arriverait bien dans le dossier mais la demande
     * resterait ouverte, et les deux parties croiraient qu'il manque encore quelque chose.</p>
     */
    @PostMapping("/enrollments/document-requests/{requestId}/fulfil")
    @PreAuthorize("hasRole('MEDECIN')")
    public ResponseEntity<RequestView> fulfil(@PathVariable UUID requestId,
                                              @RequestParam("file") MultipartFile file,
                                              Authentication auth) {
        UUID doctorId = UUID.fromString(auth.getPrincipal().toString());
        return ResponseEntity.ok(service.fulfilRequest(requestId, doctorId, file));
    }
}

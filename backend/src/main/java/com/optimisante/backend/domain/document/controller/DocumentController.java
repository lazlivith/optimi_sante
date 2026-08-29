package com.optimisante.backend.domain.document.controller;

import com.optimisante.backend.common.storage.StorageService;
import com.optimisante.backend.domain.identity.entity.Role;
import com.optimisante.backend.domain.orders.entity.Order;
import com.optimisante.backend.domain.orders.repository.OrderRepository;
import com.optimisante.backend.domain.training.entity.Enrollment;
import com.optimisante.backend.domain.training.entity.EnrollmentDocument;
import com.optimisante.backend.domain.training.repository.EnrollmentDocumentRepository;
import com.optimisante.backend.domain.training.repository.EnrollmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
public class DocumentController {

    private static final Set<String> ORDER_TYPES = Set.of("QUOTE", "INVOICE");
    private static final Set<String> ENROLLMENT_DOCUMENT_TYPES = Set.of(
            "PASSPORT", "DIPLOMA", "MEDICAL_COUNCIL_CERT", "FINANCIAL_GUARANTEE",
            "VISA_GRANT", "CONSULAR_LETTER", "ACCOMMODATION_PROOF", "OTHER");

    private final StorageService storageService;
    private final OrderRepository orderRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final EnrollmentDocumentRepository enrollmentDocumentRepository;

    @GetMapping("/{type}/{id}/download")
    public ResponseEntity<?> getDocumentDownloadUrl(
            @PathVariable String type,
            @PathVariable UUID id) {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return ResponseEntity.status(401).build();
        }
        UUID currentUserId = UUID.fromString(auth.getPrincipal().toString());
        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_" + Role.ADMIN.name()) || a.getAuthority().equals("ROLE_" + Role.SUPER_ADMIN.name()));

        String publicId;

        if (ORDER_TYPES.contains(type.toUpperCase())) {
            Order order = orderRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Order not found"));
            if (!isAdmin && !order.getUser().getId().equals(currentUserId)) {
                return ResponseEntity.status(403).build();
            }
            publicId = order.getDocumentS3Key();
        } else if ("CONVENTION".equalsIgnoreCase(type)) {
            Enrollment enrollment = enrollmentRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Enrollment not found"));
            boolean isOwnerDoctor = enrollment.getDoctor().getId().equals(currentUserId);
            boolean isOwnerPartner = enrollment.getSession().getTraining().getPartnerProfile().getUser().getId().equals(currentUserId);
            if (!isAdmin && !isOwnerDoctor && !isOwnerPartner) {
                return ResponseEntity.status(403).build();
            }
            publicId = enrollment.getConventionS3Key();
        } else if ("ATTESTATION".equalsIgnoreCase(type)) {
            Enrollment enrollment = enrollmentRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Enrollment not found"));
            boolean isOwnerDoctor = enrollment.getDoctor().getId().equals(currentUserId);
            boolean isOwnerPartner = enrollment.getSession().getTraining().getPartnerProfile().getUser().getId().equals(currentUserId);
            if (!isAdmin && !isOwnerDoctor && !isOwnerPartner) {
                return ResponseEntity.status(403).build();
            }
            publicId = enrollment.getAttestationS3Key();
        } else if (ENROLLMENT_DOCUMENT_TYPES.contains(type.toUpperCase())) {
            EnrollmentDocument document = enrollmentDocumentRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Document not found"));
            Enrollment enrollment = document.getEnrollment();
            boolean isOwnerDoctor = enrollment.getDoctor().getId().equals(currentUserId);
            boolean isOwnerPartner = enrollment.getSession().getTraining().getPartnerProfile().getUser().getId().equals(currentUserId);
            if (!isAdmin && !isOwnerDoctor && !isOwnerPartner) {
                return ResponseEntity.status(403).build();
            }
            publicId = document.getCloudinaryPublicId();
        } else {
            return ResponseEntity.badRequest().body(Map.of("error", "Type de document inconnu: " + type));
        }

        if (publicId == null || publicId.isBlank()) {
            return ResponseEntity.status(404).body(Map.of("error", "Aucun document disponible pour cette ressource"));
        }

        String downloadUrl = storageService.generatePresignedOrSignedUrl(publicId, 60);
        return ResponseEntity.ok(Map.of("downloadUrl", downloadUrl));
    }
}

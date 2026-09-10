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
import com.optimisante.backend.domain.training.finance.PartnerPayout;

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
    private final com.optimisante.backend.domain.training.finance.PartnerPayoutRepository partnerPayoutRepository;

    @GetMapping("/{type}/{id}/download")
    public ResponseEntity<?> getDocumentDownloadUrl(
            @PathVariable String type,
            @PathVariable UUID id) {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return ResponseEntity.status(401).build();
        }
        UUID currentUserId = UUID.fromString(auth.getPrincipal().toString());

        // Depuis la scission des roles (V30), « administrateur » ne suffit plus : ce
        // controleur sert a la fois les factures du negoce et les pieces de la mobilite.
        // Un unique drapeau `isAdmin` obligerait a choisir entre deux erreurs — laisser
        // ADMIN_MOBILITE dehors (il ne pouvait pas ouvrir les pieces des dossiers qu'il
        // instruit, verifie : 403) ou l'y faire entrer partout, ce qui lui ouvrirait les
        // factures clients. Les deux perimetres sont donc distingues, branche par branche.
        boolean hasLegacyAdmin = hasRole(auth, Role.ADMIN) || hasRole(auth, Role.SUPER_ADMIN);
        boolean isEcommerceAdmin = hasLegacyAdmin || hasRole(auth, Role.ADMIN_ECOMMERCE);
        boolean isMobilityAdmin = hasLegacyAdmin || hasRole(auth, Role.ADMIN_MOBILITE);

        String publicId;

        if (ORDER_TYPES.contains(type.toUpperCase())) {
            Order order = orderRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Order not found"));
            if (!isEcommerceAdmin && !order.getUser().getId().equals(currentUserId)) {
                return ResponseEntity.status(403).build();
            }
            publicId = order.getDocumentS3Key();
        } else if ("CONVENTION".equalsIgnoreCase(type)) {
            Enrollment enrollment = enrollmentRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Enrollment not found"));
            boolean isOwnerDoctor = enrollment.getDoctor().getId().equals(currentUserId);
            boolean isOwnerPartner = enrollment.getSession().getTraining().getPartnerProfile().getUser().getId().equals(currentUserId);
            if (!isMobilityAdmin && !isOwnerDoctor && !isOwnerPartner) {
                return ResponseEntity.status(403).build();
            }
            publicId = enrollment.getConventionS3Key();
        } else if ("ATTESTATION".equalsIgnoreCase(type)) {
            Enrollment enrollment = enrollmentRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Enrollment not found"));
            boolean isOwnerDoctor = enrollment.getDoctor().getId().equals(currentUserId);
            boolean isOwnerPartner = enrollment.getSession().getTraining().getPartnerProfile().getUser().getId().equals(currentUserId);
            if (!isMobilityAdmin && !isOwnerDoctor && !isOwnerPartner) {
                return ResponseEntity.status(403).build();
            }
            publicId = enrollment.getAttestationS3Key();
        } else if ("PAYOUT_STATEMENT".equalsIgnoreCase(type)) {
            PartnerPayout payout = partnerPayoutRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Reversement introuvable"));
            // Seul le partenaire beneficiaire (ou l'administration) peut telecharger son releve.
            boolean isBeneficiary = payout.getPartnerProfile().getUser().getId().equals(currentUserId);
            if (!isMobilityAdmin && !isBeneficiary) {
                return ResponseEntity.status(403).build();
            }
            publicId = payout.getStatementS3Key();
        } else if (ENROLLMENT_DOCUMENT_TYPES.contains(type.toUpperCase())) {
            EnrollmentDocument document = enrollmentDocumentRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Document not found"));
            Enrollment enrollment = document.getEnrollment();
            boolean isOwnerDoctor = enrollment.getDoctor().getId().equals(currentUserId);
            boolean isOwnerPartner = enrollment.getSession().getTraining().getPartnerProfile().getUser().getId().equals(currentUserId);
            if (!isMobilityAdmin && !isOwnerDoctor && !isOwnerPartner) {
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

    private static boolean hasRole(Authentication auth, Role role) {
        return auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_" + role.name()));
    }
}

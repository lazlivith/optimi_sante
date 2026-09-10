package com.optimisante.backend.domain.training.controller;

import com.optimisante.backend.config.security.MobilityAdmin;
import com.optimisante.backend.domain.training.dto.ServiceOptionDtos.*;
import com.optimisante.backend.domain.training.finance.ServiceOptionsPaymentService;
import com.optimisante.backend.domain.training.service.ServiceOptionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Services organises autour du sejour : assurance, hebergement, transport.
 *
 * <p>Deux publics, deux prefixes. {@code /admin/…} pour l'administration de la mobilite, qui
 * tient le catalogue et en fixe les tarifs ; {@code /enrollments/…} pour le medecin, qui
 * souscrit et regle.</p>
 *
 * <p><b>Aucune route partenaire.</b> Ce n'est pas un oubli : ces prestations sont montees et
 * payees par l'agence, le CHU n'a pas a en fixer le prix — meme regle que pour les frais de
 * dossier. L'absence de route est ce qui rend la regle infranchissable, plutot qu'un controle
 * qu'on pourrait oublier.</p>
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ServiceOptionResource {

    private final ServiceOptionService serviceOptionService;
    private final ServiceOptionsPaymentService paymentService;

    // ------------------------------------------------------------------ Catalogue admin ----

    @GetMapping("/admin/trainings/{trainingId}/service-options")
    @MobilityAdmin
    public ResponseEntity<List<CatalogOptionView>> catalog(@PathVariable UUID trainingId) {
        return ResponseEntity.ok(serviceOptionService.getCatalogForAdmin(trainingId));
    }

    /** Depose l'offre d'un type, en remplacant celle qui etait en vigueur. */
    @PostMapping("/admin/trainings/{trainingId}/service-options")
    @MobilityAdmin
    public ResponseEntity<CatalogOptionView> upsert(@PathVariable UUID trainingId,
                                                    @Valid @RequestBody CatalogOptionRequest body,
                                                    Authentication auth) {
        UUID adminId = UUID.fromString(auth.getPrincipal().toString());
        return ResponseEntity.ok(serviceOptionService.upsertCatalogOption(trainingId, adminId, body));
    }

    /** Retire une offre. Volontairement un POST : la ligne survit, seules ses souscriptions
     *  futures cessent — les souscriptions passees continuent de la referencer. */
    @PostMapping("/admin/service-options/{optionId}/deactivate")
    @MobilityAdmin
    public ResponseEntity<CatalogOptionView> deactivate(@PathVariable UUID optionId) {
        return ResponseEntity.ok(serviceOptionService.deactivateCatalogOption(optionId));
    }

    @GetMapping("/admin/enrollments/{enrollmentId}/service-options")
    @MobilityAdmin
    public ResponseEntity<ServiceSummary> summaryForAdmin(@PathVariable UUID enrollmentId) {
        return ResponseEntity.ok(serviceOptionService.getSummaryForAdmin(enrollmentId));
    }

    // -------------------------------------------------------------------- Cote medecin ----

    @GetMapping("/enrollments/{enrollmentId}/service-options")
    @PreAuthorize("hasRole('MEDECIN')")
    public ResponseEntity<ServiceSummary> summaryForDoctor(@PathVariable UUID enrollmentId,
                                                           Authentication auth) {
        UUID doctorId = UUID.fromString(auth.getPrincipal().toString());
        return ResponseEntity.ok(serviceOptionService.getSummaryForDoctor(enrollmentId, doctorId));
    }

    @PostMapping("/enrollments/{enrollmentId}/service-options")
    @PreAuthorize("hasRole('MEDECIN')")
    public ResponseEntity<ServiceSummary> select(@PathVariable UUID enrollmentId,
                                                 @Valid @RequestBody SelectOptionsRequest body,
                                                 Authentication auth) {
        UUID doctorId = UUID.fromString(auth.getPrincipal().toString());
        return ResponseEntity.ok(serviceOptionService.selectOptions(enrollmentId, doctorId, body));
    }

    @PostMapping("/enrollments/service-options/{subscriptionId}/cancel")
    @PreAuthorize("hasRole('MEDECIN')")
    public ResponseEntity<ServiceSummary> cancel(@PathVariable UUID subscriptionId,
                                                 Authentication auth) {
        UUID doctorId = UUID.fromString(auth.getPrincipal().toString());
        return ResponseEntity.ok(serviceOptionService.cancelSelection(subscriptionId, doctorId));
    }

    /** Ouvre le reglement de tous les services retenus et non encore payes. */
    @PostMapping("/enrollments/{enrollmentId}/service-options/checkout")
    @PreAuthorize("hasRole('MEDECIN')")
    public ResponseEntity<ServiceCheckoutView> checkout(@PathVariable UUID enrollmentId,
                                                        Authentication auth) {
        UUID doctorId = UUID.fromString(auth.getPrincipal().toString());
        return ResponseEntity.ok(paymentService.createServiceCheckoutSession(enrollmentId, doctorId));
    }
}

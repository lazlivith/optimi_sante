package com.optimisante.backend.domain.training.controller;

import com.optimisante.backend.config.security.MobilityAdmin;
import com.optimisante.backend.domain.training.dto.InterviewDtos.*;
import com.optimisante.backend.domain.training.service.InterviewSchedulingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Entretien de selection : trois publics, trois prefixes.
 *
 * <p>{@code /partner/…} pour l'etablissement qui propose, {@code /admin/…} pour l'administration
 * qui transmet, {@code /enrollments/…} pour le medecin qui choisit. Chaque prefixe porte deja
 * ses droits, et le service revalide la propriete du dossier — l'URL ne prouve rien.</p>
 *
 * <p>Aucune route ne permet au partenaire d'ecrire au medecin, ni au medecin de voir un
 * entretien non transmis : le circuit est impose par les routes autant que par le service.</p>
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class InterviewScheduleResource {

    private final InterviewSchedulingService service;

    // ---------------------------------------------------------------------- Partenaire ----

    @PostMapping("/partner/enrollments/{enrollmentId}/interview")
    @PreAuthorize("hasRole('CENTRE_FORMATION')")
    public ResponseEntity<ScheduleView> propose(@PathVariable UUID enrollmentId,
                                                @Valid @RequestBody ProposeRequest body,
                                                Authentication auth) {
        UUID partnerId = UUID.fromString(auth.getPrincipal().toString());
        return ResponseEntity.ok(service.propose(enrollmentId, partnerId, body));
    }

    @GetMapping("/partner/enrollments/{enrollmentId}/interviews")
    @PreAuthorize("hasRole('CENTRE_FORMATION')")
    public ResponseEntity<List<ScheduleView>> historyForPartner(@PathVariable UUID enrollmentId,
                                                                Authentication auth) {
        UUID partnerId = UUID.fromString(auth.getPrincipal().toString());
        return ResponseEntity.ok(service.historyForPartner(enrollmentId, partnerId));
    }

    // ------------------------------------------------------------------ Administration ----

    /** File de travail : les entretiens deposes par les CHU qui attendent d'etre transmis. */
    @GetMapping("/admin/interviews/awaiting-transmission")
    @MobilityAdmin
    public ResponseEntity<List<ScheduleView>> awaiting() {
        return ResponseEntity.ok(service.awaitingTransmission());
    }

    @GetMapping("/admin/enrollments/{enrollmentId}/interviews")
    @MobilityAdmin
    public ResponseEntity<List<ScheduleView>> historyForAdmin(@PathVariable UUID enrollmentId) {
        return ResponseEntity.ok(service.historyForAdmin(enrollmentId));
    }

    /** Le geste central : rendre les creneaux visibles au medecin. */
    @PostMapping("/admin/interviews/{scheduleId}/transmit")
    @MobilityAdmin
    public ResponseEntity<ScheduleView> transmit(@PathVariable UUID scheduleId,
                                                 @Valid @RequestBody(required = false) TransmitRequest body,
                                                 Authentication auth) {
        UUID adminId = UUID.fromString(auth.getPrincipal().toString());
        return ResponseEntity.ok(service.transmit(scheduleId, adminId, body));
    }

    /**
     * Abandon de la planification. Volontairement un POST et non un DELETE : la ligne n'est pas
     * supprimee mais passee en CANCELLED, pour que l'historique du dossier reste lisible.
     */
    @PostMapping("/admin/interviews/{scheduleId}/cancel")
    @MobilityAdmin
    public ResponseEntity<ScheduleView> cancel(@PathVariable UUID scheduleId,
                                               @Valid @RequestBody CancelRequest body,
                                               Authentication auth) {
        UUID adminId = UUID.fromString(auth.getPrincipal().toString());
        return ResponseEntity.ok(service.cancel(scheduleId, adminId, body));
    }

    // ------------------------------------------------------------------------- Medecin ----

    /** Renvoie 204 tant qu'aucun entretien ne lui a ete transmis : il n'y a rien a montrer. */
    @GetMapping("/enrollments/{enrollmentId}/interview")
    @PreAuthorize("hasRole('MEDECIN')")
    public ResponseEntity<ScheduleView> currentForDoctor(@PathVariable UUID enrollmentId,
                                                         Authentication auth) {
        UUID doctorId = UUID.fromString(auth.getPrincipal().toString());
        return service.currentForDoctor(enrollmentId, doctorId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/enrollments/interviews/{scheduleId}/confirm")
    @PreAuthorize("hasRole('MEDECIN')")
    public ResponseEntity<ScheduleView> confirm(@PathVariable UUID scheduleId,
                                                @Valid @RequestBody ConfirmRequest body,
                                                Authentication auth) {
        UUID doctorId = UUID.fromString(auth.getPrincipal().toString());
        return ResponseEntity.ok(service.confirm(scheduleId, doctorId, body));
    }
}

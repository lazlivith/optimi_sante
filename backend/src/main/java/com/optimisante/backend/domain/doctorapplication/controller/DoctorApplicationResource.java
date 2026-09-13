package com.optimisante.backend.domain.doctorapplication.controller;

import com.optimisante.backend.domain.doctorapplication.dto.DoctorApplicationRequestDto;
import com.optimisante.backend.domain.doctorapplication.dto.DoctorApplicationResponseDto;
import com.optimisante.backend.domain.doctorapplication.service.DoctorApplicationPaymentReconciler;
import com.optimisante.backend.domain.doctorapplication.service.DoctorApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Endpoints publics (candidat non authentifié) pour la candidature médecin payante. Le compte
 * MEDECIN n'est créé qu'après un paiement que STRIPE confirme — par le webhook, ou par la
 * vérification faite au retour du candidat (voir DoctorApplicationPaymentReconciler). Jamais sur
 * la seule parole du navigateur.
 */
@RestController
@RequestMapping("/api/v1/doctor-applications")
@RequiredArgsConstructor
public class DoctorApplicationResource {

    private final DoctorApplicationService doctorApplicationService;
    private final DoctorApplicationPaymentReconciler paymentReconciler;

    @PostMapping
    public ResponseEntity<DoctorApplicationResponseDto> submitApplication(@Valid @RequestBody DoctorApplicationRequestDto request) {
        return ResponseEntity.ok(doctorApplicationService.submitApplication(request));
    }

    @GetMapping("/{id}/status")
    public ResponseEntity<DoctorApplicationResponseDto> getStatus(@PathVariable UUID id) {
        return ResponseEntity.ok(doctorApplicationService.getStatus(id));
    }

    @GetMapping("/status-by-stripe-session")
    public ResponseEntity<DoctorApplicationResponseDto> getStatusByStripeSession(@RequestParam String sessionId) {
        return ResponseEntity.ok(paymentReconciler.statutApresRetourStripe(sessionId));
    }
}

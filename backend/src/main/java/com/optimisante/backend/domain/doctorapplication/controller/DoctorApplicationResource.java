package com.optimisante.backend.domain.doctorapplication.controller;

import com.optimisante.backend.domain.doctorapplication.dto.DoctorApplicationRequestDto;
import com.optimisante.backend.domain.doctorapplication.dto.DoctorApplicationResponseDto;
import com.optimisante.backend.domain.doctorapplication.service.DoctorApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Endpoints publics (candidat non authentifié) pour la candidature médecin payante. Le compte
 * MEDECIN n'est créé qu'après confirmation du paiement via le webhook Stripe (voir
 * DoctorApplicationService.confirmPayment), jamais depuis ce contrôleur directement.
 */
@RestController
@RequestMapping("/api/v1/doctor-applications")
@RequiredArgsConstructor
public class DoctorApplicationResource {

    private final DoctorApplicationService doctorApplicationService;

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
        return ResponseEntity.ok(doctorApplicationService.getStatusByStripeCheckoutSessionId(sessionId));
    }
}

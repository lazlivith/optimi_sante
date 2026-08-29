package com.optimisante.backend.domain.training.controller;

import com.optimisante.backend.domain.training.dto.CreateSessionRequestDto;
import com.optimisante.backend.domain.training.dto.TrainingSessionResponseDto;
import com.optimisante.backend.domain.training.service.TrainingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/partner")
@RequiredArgsConstructor
@PreAuthorize("hasRole('CENTRE_FORMATION')")
public class PartnerSessionResource {

    private final TrainingService trainingService;

    // Note : la liste des formations du partenaire (GET /partner/trainings) est servie par
    // PartnerTrainingResource, qui possède désormais le CRUD complet des formations.

    @GetMapping("/sessions")
    public ResponseEntity<List<TrainingSessionResponseDto>> getMySessions(Authentication auth) {
        UUID partnerUserId = UUID.fromString(auth.getPrincipal().toString());
        return ResponseEntity.ok(trainingService.getMySessions(partnerUserId));
    }

    @PostMapping("/sessions")
    public ResponseEntity<TrainingSessionResponseDto> createSession(
            @Valid @RequestBody CreateSessionRequestDto request,
            Authentication auth) {
        UUID partnerUserId = UUID.fromString(auth.getPrincipal().toString());
        return ResponseEntity.ok(trainingService.createSession(request, partnerUserId));
    }
}

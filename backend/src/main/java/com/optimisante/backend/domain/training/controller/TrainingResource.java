package com.optimisante.backend.domain.training.controller;

import com.optimisante.backend.domain.training.dto.LeadCaptureRequestDto;
import com.optimisante.backend.domain.training.dto.LeadCaptureResponseDto;
import com.optimisante.backend.domain.training.service.TrainingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/trainings")
@RequiredArgsConstructor
public class TrainingResource {

    private final TrainingService trainingService;

    @PostMapping("/{id}/lead-capture")
    public ResponseEntity<LeadCaptureResponseDto> captureLead(
            @PathVariable UUID id,
            @Valid @RequestBody LeadCaptureRequestDto request) {
        return ResponseEntity.ok(trainingService.captureLead(id, request));
    }
}

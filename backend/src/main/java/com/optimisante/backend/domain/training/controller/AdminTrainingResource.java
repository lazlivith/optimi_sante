package com.optimisante.backend.domain.training.controller;

import com.optimisante.backend.domain.training.dto.AdminTrainingResponseDto;
import com.optimisante.backend.domain.training.dto.TrainingRejectRequestDto;
import com.optimisante.backend.domain.training.entity.TrainingApprovalStatus;
import com.optimisante.backend.domain.training.service.AdminTrainingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/trainings")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
public class AdminTrainingResource {

    private final AdminTrainingService adminTrainingService;

    @GetMapping
    public ResponseEntity<List<AdminTrainingResponseDto>> listTrainings(
            @RequestParam(required = false) TrainingApprovalStatus status) {
        return ResponseEntity.ok(adminTrainingService.listTrainings(status));
    }

    @PatchMapping("/{id}/approve")
    public ResponseEntity<AdminTrainingResponseDto> approve(@PathVariable UUID id) {
        return ResponseEntity.ok(adminTrainingService.approve(id));
    }

    @PatchMapping("/{id}/reject")
    public ResponseEntity<AdminTrainingResponseDto> reject(
            @PathVariable UUID id, @Valid @RequestBody TrainingRejectRequestDto dto) {
        return ResponseEntity.ok(adminTrainingService.reject(id, dto.reason()));
    }
}

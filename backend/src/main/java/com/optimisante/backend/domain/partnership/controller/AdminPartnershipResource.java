package com.optimisante.backend.domain.partnership.controller;

import com.optimisante.backend.common.storage.StorageService;
import com.optimisante.backend.domain.partnership.dto.PartnershipRequestResponseDto;
import com.optimisante.backend.domain.partnership.repository.PartnershipRequestRepository;
import com.optimisante.backend.domain.partnership.service.PartnershipService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/partnership-requests")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
public class AdminPartnershipResource {

    private final PartnershipService partnershipService;
    private final PartnershipRequestRepository partnershipRequestRepository;
    private final StorageService storageService;

    @GetMapping
    public ResponseEntity<List<PartnershipRequestResponseDto>> listRequests() {
        return ResponseEntity.ok(partnershipService.listRequests());
    }

    @GetMapping("/{id}/document")
    public ResponseEntity<Map<String, String>> getSubmittedDocument(@PathVariable UUID id) {
        String fileKey = partnershipRequestRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Partnership request not found"))
                .getConventionFileKey();
        return ResponseEntity.ok(Map.of("downloadUrl", storageService.generatePresignedOrSignedUrl(fileKey, 60)));
    }

    @PatchMapping("/{id}/approve")
    public ResponseEntity<PartnershipRequestResponseDto> approve(@PathVariable UUID id) {
        return ResponseEntity.ok(partnershipService.approveRequest(id));
    }

    @PatchMapping("/{id}/reject")
    public ResponseEntity<PartnershipRequestResponseDto> reject(
            @PathVariable UUID id,
            @RequestBody(required = false) Map<String, String> body) {
        String reason = body != null ? body.get("reason") : null;
        return ResponseEntity.ok(partnershipService.rejectRequest(id, reason));
    }
}

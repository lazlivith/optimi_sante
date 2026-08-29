package com.optimisante.backend.domain.partnership.controller;

import com.optimisante.backend.domain.partnership.dto.PartnershipRequestResponseDto;
import com.optimisante.backend.domain.partnership.service.PartnershipService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/partnership")
@RequiredArgsConstructor
public class PartnershipResource {

    private final PartnershipService partnershipService;

    @GetMapping("/convention-template")
    public ResponseEntity<Map<String, String>> getConventionTemplate() {
        return ResponseEntity.ok(Map.of("downloadUrl", partnershipService.getConventionTemplateUrl()));
    }

    @PostMapping(value = "/requests", consumes = "multipart/form-data")
    public ResponseEntity<PartnershipRequestResponseDto> submitRequest(
            @RequestParam String institutionName,
            @RequestParam(required = false) String finessAccreditation,
            @RequestParam String contactPersonName,
            @RequestParam String contactEmail,
            @RequestParam String contactPhone,
            @RequestParam String address,
            @RequestParam("conventionFile") MultipartFile conventionFile) {
        return ResponseEntity.ok(partnershipService.submitRequest(
                institutionName, finessAccreditation, contactPersonName, contactEmail, contactPhone, address, conventionFile));
    }
}

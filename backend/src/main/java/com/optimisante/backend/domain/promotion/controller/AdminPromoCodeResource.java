package com.optimisante.backend.domain.promotion.controller;

import com.optimisante.backend.domain.promotion.dto.PromoCodeRequestDto;
import com.optimisante.backend.domain.promotion.dto.PromoCodeResponseDto;
import com.optimisante.backend.domain.promotion.service.PromoCodeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/promo-codes")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
public class AdminPromoCodeResource {

    private final PromoCodeService promoCodeService;

    @GetMapping
    public ResponseEntity<List<PromoCodeResponseDto>> listCodes() {
        return ResponseEntity.ok(promoCodeService.listCodes());
    }

    @PostMapping
    public ResponseEntity<PromoCodeResponseDto> createCode(@Valid @RequestBody PromoCodeRequestDto request) {
        return ResponseEntity.ok(promoCodeService.createCode(request));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<PromoCodeResponseDto> setActive(@PathVariable UUID id, @RequestParam boolean active) {
        return ResponseEntity.ok(promoCodeService.setActive(id, active));
    }
}

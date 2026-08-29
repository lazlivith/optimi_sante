package com.optimisante.backend.domain.promotion.controller;

import com.optimisante.backend.domain.promotion.service.PromoCodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Validation d'un code promo côté panier/checkout, avant confirmation de la commande — permet
 * d'afficher la remise au client sans encore consommer l'usage du code (cf. PromoCodeService).
 */
@RestController
@RequestMapping("/api/v1/promo-codes")
@RequiredArgsConstructor
public class PromoCodeResource {

    private final PromoCodeService promoCodeService;

    @PostMapping("/validate")
    public ResponseEntity<Map<String, Object>> validate(@RequestBody Map<String, Object> request) {
        String code = String.valueOf(request.get("code"));
        BigDecimal orderAmount = new BigDecimal(String.valueOf(request.get("orderAmount")));

        PromoCodeService.DiscountResult result = promoCodeService.validateAndComputeDiscount(code, orderAmount);
        return ResponseEntity.ok(Map.of(
                "valid", true,
                "discountAmount", result.discountAmount(),
                "discountType", result.promoCode().getDiscountType().name()
        ));
    }
}

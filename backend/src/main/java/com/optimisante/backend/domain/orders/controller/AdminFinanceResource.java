package com.optimisante.backend.domain.orders.controller;

import com.optimisante.backend.domain.orders.dto.BestSellerDto;
import com.optimisante.backend.domain.orders.dto.FinanceSummaryDto;
import com.optimisante.backend.domain.orders.dto.RecentSaleDto;
import com.optimisante.backend.domain.orders.service.FinanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/finance")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
public class AdminFinanceResource {

    private final FinanceService financeService;

    @GetMapping("/summary")
    public ResponseEntity<FinanceSummaryDto> getSummary() {
        return ResponseEntity.ok(financeService.getSummary());
    }

    @GetMapping("/best-sellers")
    public ResponseEntity<List<BestSellerDto>> getBestSellers(@RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(financeService.getBestSellers(limit));
    }

    @GetMapping("/recent-sales")
    public ResponseEntity<List<RecentSaleDto>> getRecentSales(@RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(financeService.getRecentSales(limit));
    }
}

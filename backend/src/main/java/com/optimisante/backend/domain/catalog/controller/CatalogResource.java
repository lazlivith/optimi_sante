package com.optimisante.backend.domain.catalog.controller;

import com.optimisante.backend.domain.catalog.dto.CategoryResponseDto;
import com.optimisante.backend.domain.catalog.dto.ProductResponseDto;
import com.optimisante.backend.domain.catalog.service.CatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/catalog")
@RequiredArgsConstructor
public class CatalogResource {

    private final CatalogService catalogService;

    @GetMapping("/categories")
    public ResponseEntity<List<CategoryResponseDto>> getCategories() {
        return ResponseEntity.ok(catalogService.getCategories());
    }

    @GetMapping("/products")
    public ResponseEntity<Page<ProductResponseDto>> searchProducts(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(catalogService.searchProducts(search, categoryId, minPrice, maxPrice, pageable));
    }

    @GetMapping("/products/{slug}")
    public ResponseEntity<ProductResponseDto> getProductBySlug(@PathVariable String slug) {
        return ResponseEntity.ok(catalogService.getProductBySlug(slug));
    }
}

package com.optimisante.backend.domain.catalog.controller;

import com.optimisante.backend.domain.catalog.dto.AdminProductRequestDto;
import com.optimisante.backend.domain.catalog.dto.AdminProductResponseDto;
import com.optimisante.backend.domain.catalog.dto.CategoryResponseDto;
import com.optimisante.backend.domain.catalog.service.AdminCatalogService;
import com.optimisante.backend.domain.catalog.service.CatalogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/catalog")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
public class AdminCatalogResource {

    private final AdminCatalogService adminCatalogService;
    private final CatalogService catalogService;

    @GetMapping("/products")
    public ResponseEntity<Page<AdminProductResponseDto>> listProducts(@PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(adminCatalogService.listProducts(pageable));
    }

    @PostMapping("/products")
    public ResponseEntity<AdminProductResponseDto> createProduct(@Valid @RequestBody AdminProductRequestDto request) {
        return ResponseEntity.ok(adminCatalogService.createProduct(request));
    }

    @PutMapping("/products/{id}")
    public ResponseEntity<AdminProductResponseDto> updateProduct(
            @PathVariable UUID id,
            @Valid @RequestBody AdminProductRequestDto request) {
        return ResponseEntity.ok(adminCatalogService.updateProduct(id, request));
    }

    @PatchMapping("/products/{id}/status")
    public ResponseEntity<AdminProductResponseDto> setProductActive(
            @PathVariable UUID id,
            @RequestParam boolean active) {
        return ResponseEntity.ok(adminCatalogService.setProductActive(id, active));
    }

    @GetMapping("/categories")
    public ResponseEntity<List<CategoryResponseDto>> listCategories() {
        return ResponseEntity.ok(catalogService.getCategories());
    }
}

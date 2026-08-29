package com.optimisante.backend.domain.catalog.service;

import com.optimisante.backend.config.tenant.TenantContext;
import com.optimisante.backend.domain.catalog.dto.AdminProductRequestDto;
import com.optimisante.backend.domain.catalog.dto.AdminProductResponseDto;
import com.optimisante.backend.domain.catalog.entity.Category;
import com.optimisante.backend.domain.catalog.entity.Product;
import com.optimisante.backend.domain.catalog.repository.AdminProductRow;
import com.optimisante.backend.domain.catalog.repository.CategoryRepository;
import com.optimisante.backend.domain.catalog.repository.ProductRepository;
import com.optimisante.backend.domain.identity.entity.Tenant;
import com.optimisante.backend.domain.identity.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminCatalogService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final TenantRepository tenantRepository;

    @Transactional(readOnly = true)
    public Page<AdminProductResponseDto> listProducts(Pageable pageable) {
        // Requête native (voir ProductRepository.findAllForAdmin) : contourne volontairement
        // @SQLRestriction("deleted_at IS NULL AND is_active = true") pour que l'admin voie
        // aussi les produits désactivés (et puisse les réactiver).
        Page<AdminProductRow> rows = productRepository.findAllForAdmin(pageable);

        Map<UUID, String> categoryNames = categoryRepository.findAllById(
                rows.getContent().stream()
                        .map(AdminProductRow::getCategoryId)
                        .filter(id -> id != null)
                        .collect(Collectors.toSet())
        ).stream().collect(Collectors.toMap(Category::getId, Category::getName));

        return rows.map(row -> toResponseDto(row, categoryNames.get(row.getCategoryId())));
    }

    @Transactional
    public AdminProductResponseDto createProduct(AdminProductRequestDto dto) {
        UUID tenantId = requireTenantId();
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant not found"));

        Category category = dto.categoryId() != null
                ? categoryRepository.findById(dto.categoryId()).orElse(null)
                : null;

        String slug = generateUniqueSlug(tenantId, dto.name());

        Product product = Product.builder()
                .tenant(tenant)
                .sku(dto.sku())
                .name(dto.name())
                .slug(slug)
                .description(dto.description())
                .basePrice(dto.basePrice())
                .stockQuantity(dto.stockQuantity() != null ? dto.stockQuantity() : 0)
                .stockThreshold(dto.stockThreshold() != null ? dto.stockThreshold() : 5)
                .isQuoteOnly(Boolean.TRUE.equals(dto.isQuoteOnly()))
                .isActive(true)
                .category(category)
                .imageUrl(dto.imageUrl())
                .promoPrice(dto.promoPrice())
                .promoStartsAt(dto.promoStartsAt())
                .promoEndsAt(dto.promoEndsAt())
                .build();

        return toResponseDto(productRepository.save(product));
    }

    @Transactional
    public AdminProductResponseDto updateProduct(UUID productId, AdminProductRequestDto dto) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException(
                        "Product not found or inactive — réactivez-le avant de le modifier"));

        product.setSku(dto.sku());
        product.setName(dto.name());
        product.setDescription(dto.description());
        product.setBasePrice(dto.basePrice());
        if (dto.stockQuantity() != null) product.setStockQuantity(dto.stockQuantity());
        if (dto.stockThreshold() != null) product.setStockThreshold(dto.stockThreshold());
        if (dto.isQuoteOnly() != null) product.setIsQuoteOnly(dto.isQuoteOnly());
        if (dto.imageUrl() != null) product.setImageUrl(dto.imageUrl());
        if (dto.categoryId() != null) {
            categoryRepository.findById(dto.categoryId()).ifPresent(product::setCategory);
        }
        // Toujours réassignés (pas de vérification != null) pour permettre de retirer une
        // promotion existante depuis le formulaire d'édition, pas seulement d'en ajouter une.
        product.setPromoPrice(dto.promoPrice());
        product.setPromoStartsAt(dto.promoStartsAt());
        product.setPromoEndsAt(dto.promoEndsAt());

        return toResponseDto(productRepository.save(product));
    }

    /**
     * Active/désactive un produit. Contrairement à updateProduct, passe par une requête UPDATE
     * native (ProductRepository.setActiveForAdmin) — c'est la seule façon de réactiver un
     * produit déjà désactivé, puisque findById() ne le retournerait plus (@SQLRestriction).
     */
    @Transactional
    public AdminProductResponseDto setProductActive(UUID productId, boolean active) {
        AdminProductRow existing = productRepository.findByIdForAdmin(productId)
                .orElseThrow(() -> new RuntimeException("Product not found"));
        productRepository.setActiveForAdmin(productId, active);
        AdminProductRow updated = productRepository.findByIdForAdmin(productId).orElseThrow();
        String categoryName = existing.getCategoryId() != null
                ? categoryRepository.findById(existing.getCategoryId()).map(Category::getName).orElse(null)
                : null;
        return toResponseDto(updated, categoryName);
    }

    private String generateUniqueSlug(UUID tenantId, String name) {
        String base = slugify(name);
        String slug = base;
        int suffix = 2;
        while (productRepository.findByTenantIdAndSlug(tenantId, slug).isPresent()) {
            slug = base + "-" + suffix;
            suffix++;
        }
        return slug;
    }

    private String slugify(String input) {
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        String slug = normalized.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        return slug.isBlank() ? "produit" : slug;
    }

    private UUID requireTenantId() {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new IllegalStateException("Tenant context is required");
        }
        return tenantId;
    }

    private AdminProductResponseDto toResponseDto(Product p) {
        return AdminProductResponseDto.builder()
                .id(p.getId())
                .sku(p.getSku())
                .name(p.getName())
                .slug(p.getSlug())
                .description(p.getDescription())
                .basePrice(p.getBasePrice())
                .stockQuantity(p.getStockQuantity())
                .stockThreshold(p.getStockThreshold())
                .isQuoteOnly(p.getIsQuoteOnly())
                .isActive(p.getIsActive())
                .imageUrl(p.getImageUrl())
                .categoryId(p.getCategory() != null ? p.getCategory().getId() : null)
                .categoryName(p.getCategory() != null ? p.getCategory().getName() : null)
                .promoPrice(p.getPromoPrice())
                .promoStartsAt(p.getPromoStartsAt())
                .promoEndsAt(p.getPromoEndsAt())
                .build();
    }

    private AdminProductResponseDto toResponseDto(AdminProductRow row, String categoryName) {
        return AdminProductResponseDto.builder()
                .id(row.getId())
                .sku(row.getSku())
                .name(row.getName())
                .slug(row.getSlug())
                .description(row.getDescription())
                .basePrice(row.getBasePrice())
                .stockQuantity(row.getStockQuantity())
                .stockThreshold(row.getStockThreshold())
                .isQuoteOnly(row.getIsQuoteOnly())
                .isActive(row.getIsActive())
                .imageUrl(row.getImageUrl())
                .categoryId(row.getCategoryId())
                .categoryName(categoryName)
                .promoPrice(row.getPromoPrice())
                .promoStartsAt(row.getPromoStartsAt())
                .promoEndsAt(row.getPromoEndsAt())
                .build();
    }
}

package com.optimisante.backend.domain.catalog.service;

import com.optimisante.backend.config.tenant.TenantContext;
import com.optimisante.backend.domain.catalog.dto.CategoryResponseDto;
import com.optimisante.backend.domain.catalog.dto.CategorySummaryDto;
import com.optimisante.backend.domain.catalog.dto.ProductResponseDto;
import com.optimisante.backend.domain.catalog.entity.Category;
import com.optimisante.backend.domain.catalog.entity.Product;
import com.optimisante.backend.domain.catalog.repository.CategoryRepository;
import com.optimisante.backend.domain.catalog.repository.ProductRepository;
import com.optimisante.backend.domain.catalog.repository.ProductSpecification;
import com.optimisante.backend.domain.identity.entity.Role;
import com.optimisante.backend.domain.identity.repository.CompanyProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CatalogService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final CompanyProfileRepository companyProfileRepository;

    @Transactional(readOnly = true)
    public List<CategoryResponseDto> getCategories() {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new IllegalStateException("Tenant context is required");
        }
        
        List<Category> rootCategories = categoryRepository.findByTenantIdAndParentIsNull(tenantId);
        return rootCategories.stream()
                .map(this::mapToCategoryDto)
                .collect(Collectors.toList());
    }

    private CategoryResponseDto mapToCategoryDto(Category category) {
        List<CategoryResponseDto> subcategories = category.getSubcategories().stream()
                .map(this::mapToCategoryDto)
                .collect(Collectors.toList());
                
        return new CategoryResponseDto(
                category.getId(),
                category.getName(),
                category.getSlug(),
                subcategories
        );
    }

    @Transactional(readOnly = true)
    public Page<ProductResponseDto> searchProducts(String search, UUID categoryId, BigDecimal minPrice, BigDecimal maxPrice, Pageable pageable) {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new IllegalStateException("Tenant context is required");
        }

        Specification<Product> spec = Specification.where(ProductSpecification.withTenantId(tenantId))
                .and(ProductSpecification.fetchCategory())
                .and(ProductSpecification.search(search))
                .and(ProductSpecification.withCategoryId(categoryId))
                .and(ProductSpecification.priceBetween(minPrice, maxPrice));

        Page<Product> products = productRepository.findAll(spec, pageable);
        
        BigDecimal b2bDiscountRate = getB2BDiscountRate();
        
        return products.map(product -> mapToProductDto(product, b2bDiscountRate));
    }

    @Transactional(readOnly = true)
    public ProductResponseDto getProductBySlug(String slug) {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new IllegalStateException("Tenant context is required");
        }

        Product product = productRepository.findByTenantIdAndSlug(tenantId, slug)
                .orElseThrow(() -> new RuntimeException("Product not found"));
                
        BigDecimal b2bDiscountRate = getB2BDiscountRate();
        return mapToProductDto(product, b2bDiscountRate);
    }

    private BigDecimal getB2BDiscountRate() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            return BigDecimal.ZERO;
        }

        boolean isB2B = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_" + Role.CLIENT_B2B.name()));

        if (isB2B) {
            try {
                UUID userId = UUID.fromString(authentication.getPrincipal().toString());
                return companyProfileRepository.findByUserId(userId)
                        .map(profile -> profile.getB2bDiscountRate())
                        .orElse(BigDecimal.ZERO);
            } catch (Exception e) {
                // If ID is not a valid UUID or user not found, fallback to 0
                return BigDecimal.ZERO;
            }
        }
        
        return BigDecimal.ZERO;
    }

    private ProductResponseDto mapToProductDto(Product product, BigDecimal b2bDiscountRate) {
        // Le prix promotionnel (s'il est actif) sert de base au calcul, la remise B2B
        // s'applique ensuite par-dessus — même point de vérité que le calcul au checkout
        // (Product.getEffectiveBasePrice), pour ne jamais afficher un prix qui ne serait pas
        // celui réellement facturé.
        BigDecimal effectiveBasePrice = product.getEffectiveBasePrice();
        BigDecimal finalPrice = effectiveBasePrice;

        if (b2bDiscountRate != null && b2bDiscountRate.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal multiplier = BigDecimal.ONE.subtract(b2bDiscountRate.divide(BigDecimal.valueOf(100)));
            finalPrice = effectiveBasePrice.multiply(multiplier).setScale(2, RoundingMode.HALF_UP);
        }

        CategorySummaryDto categoryDto = null;
        if (product.getCategory() != null) {
            categoryDto = new CategorySummaryDto(
                    product.getCategory().getId(),
                    product.getCategory().getName(),
                    product.getCategory().getSlug()
            );
        }

        return new ProductResponseDto(
                product.getId(),
                product.getSku(),
                product.getName(),
                product.getSlug(),
                product.getDescription(),
                product.getBasePrice(),
                finalPrice,
                b2bDiscountRate,
                product.getStockQuantity(),
                product.getIsQuoteOnly(),
                product.getImageUrl(),
                categoryDto,
                product.isPromoActive(),
                product.isPromoActive() ? product.getPromoEndsAt() : null
        );
    }
}

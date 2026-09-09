package com.optimisante.backend.domain.catalog.repository;

import com.optimisante.backend.domain.catalog.entity.Product;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.UUID;

public class ProductSpecification {

    public static Specification<Product> withTenantId(UUID tenantId) {
        return (root, query, cb) -> {
            if (tenantId == null) return null;
            return cb.equal(root.get("tenant").get("id"), tenantId);
        };
    }

    public static Specification<Product> search(String search) {
        return (root, query, cb) -> {
            if (!StringUtils.hasText(search)) return null;
            String pattern = "%" + search.toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("name")), pattern),
                    cb.like(cb.lower(root.get("sku")), pattern)
            );
        };
    }

    public static Specification<Product> withCategoryId(UUID categoryId) {
        return (root, query, cb) -> {
            if (categoryId == null) return null;
            return cb.equal(root.get("category").get("id"), categoryId);
        };
    }

    public static Specification<Product> priceBetween(BigDecimal minPrice, BigDecimal maxPrice) {
        return (root, query, cb) -> {
            if (minPrice != null && maxPrice != null) {
                return cb.between(root.get("basePrice"), minPrice, maxPrice);
            } else if (minPrice != null) {
                return cb.greaterThanOrEqualTo(root.get("basePrice"), minPrice);
            } else if (maxPrice != null) {
                return cb.lessThanOrEqualTo(root.get("basePrice"), maxPrice);
            }
            return null;
        };
    }
    
    /**
     * Produits dont la promotion est <b>active maintenant</b>.
     *
     * <p>La condition ne se resume pas a « un prix promo est renseigne » : une promotion porte
     * une fenetre de validite, et une promotion terminee ou pas encore commencee ne doit pas
     * remonter. Les bornes nulles valent « depuis toujours » et « sans fin » — c'est la meme
     * regle que {@code Product.getEffectiveBasePrice()}, qui decide du prix reellement
     * facture. Les deux doivent dire la meme chose, sans quoi la boutique afficherait une
     * promotion qui ne s'appliquerait pas au paiement.</p>
     */
    public static Specification<Product> onPromoOnly(Boolean promoOnly) {
        return (root, query, cb) -> {
            if (!Boolean.TRUE.equals(promoOnly)) return null;
            var maintenant = java.time.OffsetDateTime.now();
            return cb.and(
                    cb.isNotNull(root.get("promoPrice")),
                    cb.or(cb.isNull(root.get("promoStartsAt")),
                          cb.lessThanOrEqualTo(root.get("promoStartsAt"), maintenant)),
                    cb.or(cb.isNull(root.get("promoEndsAt")),
                          cb.greaterThanOrEqualTo(root.get("promoEndsAt"), maintenant))
            );
        };
    }

    public static Specification<Product> fetchCategory() {
        return (root, query, cb) -> {
            // Check if it's a count query, we don't want to fetch if it's a count query
            if (Long.class != query.getResultType()) {
                root.fetch("category", JoinType.LEFT);
            }
            return cb.conjunction();
        };
    }
}

package com.optimisante.backend.domain.catalog.repository;

import com.optimisante.backend.domain.catalog.entity.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductRepository extends JpaRepository<Product, UUID>, JpaSpecificationExecutor<Product> {

    // Find by slug within a specific tenant
    Optional<Product> findByTenantIdAndSlug(UUID tenantId, String slug);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.id = :id")
    Optional<Product> findByIdWithPessimisticLock(@Param("id") UUID id);

    // --- Requêtes admin natives : Product porte @SQLRestriction("deleted_at IS NULL AND
    // is_active = true"), qui s'applique à TOUTE requête hydratant l'entité, y compris les
    // requêtes natives. Ces méthodes retournent une projection (AdminProductRow), jamais
    // l'entité Product, pour que l'admin voie aussi les produits désactivés.
    @Query(value = """
            SELECT p.id, p.sku, p.name, p.slug, p.description, p.base_price AS basePrice,
                   p.stock_quantity AS stockQuantity, p.stock_threshold AS stockThreshold,
                   p.is_quote_only AS isQuoteOnly, p.is_active AS isActive, p.image_url AS imageUrl,
                   p.category_id AS categoryId, p.promo_price AS promoPrice,
                   p.promo_starts_at AS promoStartsAt, p.promo_ends_at AS promoEndsAt
            FROM products p
            WHERE p.deleted_at IS NULL
            ORDER BY p.is_active DESC, p.created_at DESC
            """,
            countQuery = "SELECT count(*) FROM products p WHERE p.deleted_at IS NULL",
            nativeQuery = true)
    Page<AdminProductRow> findAllForAdmin(Pageable pageable);

    @Query(value = """
            SELECT p.id, p.sku, p.name, p.slug, p.description, p.base_price AS basePrice,
                   p.stock_quantity AS stockQuantity, p.stock_threshold AS stockThreshold,
                   p.is_quote_only AS isQuoteOnly, p.is_active AS isActive, p.image_url AS imageUrl,
                   p.category_id AS categoryId, p.promo_price AS promoPrice,
                   p.promo_starts_at AS promoStartsAt, p.promo_ends_at AS promoEndsAt
            FROM products p
            WHERE p.id = :id AND p.deleted_at IS NULL
            """,
            nativeQuery = true)
    Optional<AdminProductRow> findByIdForAdmin(@Param("id") UUID id);

    @Modifying
    @Transactional
    @Query(value = "UPDATE products SET is_active = :active WHERE id = :id", nativeQuery = true)
    int setActiveForAdmin(@Param("id") UUID id, @Param("active") boolean active);
}

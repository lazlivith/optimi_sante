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
    /**
     * Liste filtrée du catalogue pour l'administration.
     *
     * <p><b>Tous les filtres passent en {@code String}, y compris l'identifiant de catégorie
     * et le drapeau d'activité.</b> Ce n'est pas une facilité : PostgreSQL ne peut pas
     * inférer le type d'un paramètre nul, et un {@code String} nul lié sans indication est
     * transmis en {@code bytea} — la requête échoue alors sur
     * {@code function lower(bytea) does not exist}, à l'exécution seulement, et uniquement
     * quand le filtre est vide. Le {@code CAST(:param AS text)} lève l'ambiguïté ; un seul
     * type de paramètre pour tous les filtres évite d'avoir à se rappeler lequel est
     * concerné.</p>
     *
     * <p>Le tri est choisi par une liste blanche côté SQL plutôt qu'en laissant Spring Data
     * ajouter un {@code ORDER BY} au {@link Pageable} : sur une requête native qui porte déjà
     * le sien, les deux se cumulent et la requête devient invalide. C'est exactement ce qui
     * s'est produit tant que le paramètre exposé s'appelait {@code sort}, nom que Spring
     * réserve au {@link Pageable} — d'où {@code sortBy} côté API.</p>
     *
     * @param search      terme recherché dans le nom ou la référence, {@code null} si aucun
     * @param categoryId  UUID de catégorie sous forme de texte, {@code null} si aucun
     * @param activeState {@code "ACTIVE"}, {@code "INACTIVE"} ou {@code null} pour les deux
     * @param lowStock    ne remonter que les produits sous leur seuil de réapprovisionnement
     * @param sortBy      {@code "name_asc"}, {@code "price_asc"}, {@code "price_desc"} ou
     *                    {@code null} pour l'ordre par défaut (actifs d'abord, plus récents
     *                    en tête)
     */
    @Query(value = """
            SELECT p.id, p.sku, p.name, p.slug, p.description, p.base_price AS basePrice,
                   p.stock_quantity AS stockQuantity, p.stock_threshold AS stockThreshold,
                   p.is_quote_only AS isQuoteOnly, p.is_active AS isActive, p.image_url AS imageUrl,
                   p.category_id AS categoryId, p.promo_price AS promoPrice,
                   p.promo_starts_at AS promoStartsAt, p.promo_ends_at AS promoEndsAt
            FROM products p
            WHERE p.deleted_at IS NULL
              AND (CAST(:search AS text) IS NULL
                   OR lower(p.name) LIKE lower(concat('%', CAST(:search AS text), '%'))
                   OR lower(p.sku)  LIKE lower(concat('%', CAST(:search AS text), '%')))
              AND (CAST(:categoryId AS text) IS NULL
                   OR p.category_id = CAST(CAST(:categoryId AS text) AS uuid))
              AND (CAST(:activeState AS text) IS NULL
                   OR (CAST(:activeState AS text) = 'ACTIVE'   AND p.is_active = true)
                   OR (CAST(:activeState AS text) = 'INACTIVE' AND p.is_active = false))
              AND (:lowStock = false OR p.stock_quantity <= p.stock_threshold)
            ORDER BY
              CASE WHEN CAST(:sortBy AS text) = 'name_asc'    THEN p.name END ASC,
              CASE WHEN CAST(:sortBy AS text) = 'price_asc'   THEN p.base_price END ASC,
              CASE WHEN CAST(:sortBy AS text) = 'price_desc'  THEN p.base_price END DESC,
              p.is_active DESC, p.created_at DESC
            """,
            countQuery = """
            SELECT count(*)
            FROM products p
            WHERE p.deleted_at IS NULL
              AND (CAST(:search AS text) IS NULL
                   OR lower(p.name) LIKE lower(concat('%', CAST(:search AS text), '%'))
                   OR lower(p.sku)  LIKE lower(concat('%', CAST(:search AS text), '%')))
              AND (CAST(:categoryId AS text) IS NULL
                   OR p.category_id = CAST(CAST(:categoryId AS text) AS uuid))
              AND (CAST(:activeState AS text) IS NULL
                   OR (CAST(:activeState AS text) = 'ACTIVE'   AND p.is_active = true)
                   OR (CAST(:activeState AS text) = 'INACTIVE' AND p.is_active = false))
              AND (:lowStock = false OR p.stock_quantity <= p.stock_threshold)
            """,
            nativeQuery = true)
    Page<AdminProductRow> searchForAdmin(@Param("search") String search,
                                         @Param("categoryId") String categoryId,
                                         @Param("activeState") String activeState,
                                         @Param("lowStock") boolean lowStock,
                                         @Param("sortBy") String sortBy,
                                         Pageable pageable);

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

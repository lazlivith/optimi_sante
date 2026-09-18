package com.optimisante.backend.domain.catalog.supplier;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface SupplierRepository extends JpaRepository<Supplier, UUID> {

    List<Supplier> findByTenantIdOrderByCompanyNameAsc(UUID tenantId);

    Optional<Supplier> findByTenantIdAndCodeIgnoreCase(UUID tenantId, String code);

    boolean existsByTenantIdAndCodeIgnoreCase(UUID tenantId, String code);

    /**
     * Nombre de produits par fournisseur, en une requête.
     *
     * <p>Native : {@code Product} porte un {@code @SQLRestriction} qui masque les produits
     * désactivés, et la fiche d'un fournisseur doit compter tout ce qu'il a livré, pas seulement
     * ce qui est en vente aujourd'hui.</p>
     */
    @Query(value = """
            SELECT supplier_id AS id, count(*) AS total
              FROM products
             WHERE supplier_id IS NOT NULL AND deleted_at IS NULL
             GROUP BY supplier_id
            """, nativeQuery = true)
    List<Map<String, Object>> compterProduitsParFournisseur();

    @Query(value = "SELECT count(*) FROM products WHERE supplier_id = :id AND deleted_at IS NULL",
            nativeQuery = true)
    long compterProduits(@Param("id") UUID supplierId);
}

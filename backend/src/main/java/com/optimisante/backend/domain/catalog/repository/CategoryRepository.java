package com.optimisante.backend.domain.catalog.repository;

import com.optimisante.backend.domain.catalog.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CategoryRepository extends JpaRepository<Category, UUID> {
    
    // Find all root categories (no parent) for a specific tenant
    List<Category> findByTenantIdAndParentIsNull(UUID tenantId);
    
    // For fetching children if needed manually, though they are fetched via relationships
    List<Category> findByTenantIdAndParentId(UUID tenantId, UUID parentId);

    /**
     * Catégories du tenant avec leur nombre de produits, en une seule requête.
     *
     * <p>Requête native délibérée : {@code Product} porte
     * {@code @SQLRestriction("deleted_at IS NULL AND is_active = true")}, qu'Hibernate
     * applique à toute requête touchant l'entité, jointures comprises. La même requête en
     * JPQL ne compterait donc que les produits actifs, et une catégorie ne contenant que des
     * produits désactivés s'afficherait comme vide — l'administrateur la croirait inutilisée.
     * Ici le filtre est explicite : on exclut les produits supprimés, pas les désactivés.</p>
     *
     * <p>Compter côté application imposerait une requête par catégorie, soit plus de deux
     * cents pour afficher un menu déroulant.</p>
     *
     * <p>Le {@code LEFT JOIN} est indispensable : un {@code JOIN} ferait disparaître les
     * catégories vides, or ce sont précisément celles qu'il faut pouvoir repérer. Et la
     * condition sur {@code deleted_at} appartient au {@code ON}, pas au {@code WHERE} : dans
     * un {@code WHERE} elle annulerait l'effet du {@code LEFT JOIN}.</p>
     */
    @Query(value = """
            SELECT c.id, c.name, c.slug, count(p.id) AS productCount
            FROM categories c
            LEFT JOIN products p ON p.category_id = c.id AND p.deleted_at IS NULL
            WHERE c.tenant_id = :tenantId
            GROUP BY c.id, c.name, c.slug
            ORDER BY c.name ASC
            """, nativeQuery = true)
    List<CategoryUsageRow> findAllWithProductCount(@Param("tenantId") UUID tenantId);
}

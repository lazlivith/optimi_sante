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

    /** Rattachement d'un produit importé : le fichier fournisseur nomme la catégorie, il ne l'identifie pas. */
    java.util.Optional<Category> findFirstByTenantIdAndNameIgnoreCase(UUID tenantId, String name);
    
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

    /**
     * Nombre de produits achetables et photo représentative, par catégorie.
     *
     * <p>Alimente la boutique : voir {@link ShopCategoryRow} pour le détail des trois choix
     * (une image issue d'un vrai produit, stable dans le temps, et un comptage restreint aux
     * produits actifs).</p>
     *
     * <p><b>Les images d'attente sont écartées.</b> 109 produits portent encore
     * {@code medical-placeholder.jpg} ou une photo Unsplash : ce sont des fiches à compléter,
     * pas des visuels. Retenues comme image de rayon, elles reproduiraient précisément le
     * défaut qu'on corrige. Seize catégories non vides n'ont ainsi aucune photo — elles
     * remontent avec {@code imageUrl} à {@code null}, et c'est au front de décider quoi en
     * faire. Cette règle existe en double, ici et dans {@code visuelAFaire} côté client ; les
     * deux doivent être modifiées ensemble le jour où la liste des images d'attente change.</p>
     *
     * <p>Une seule requête pour les 211 catégories. La sous-requête corrélée est indexée par
     * {@code idx_products_category} ; la faire remonter produit par produit côté application
     * coûterait 211 allers-retours pour afficher une grille de quatorze vignettes.</p>
     */
    @Query(value = """
            SELECT c.id AS id,
                   count(p.id) AS productCount,
                   (SELECT p2.image_url
                      FROM products p2
                     WHERE p2.category_id = c.id
                       AND p2.deleted_at IS NULL
                       AND p2.is_active = true
                       AND p2.image_url IS NOT NULL
                       AND p2.image_url <> ''
                       AND p2.image_url NOT LIKE '%medical-placeholder%'
                       AND p2.image_url NOT LIKE '%unsplash%'
                     ORDER BY p2.created_at ASC, p2.id ASC
                     LIMIT 1) AS imageUrl
            FROM categories c
            LEFT JOIN products p
                   ON p.category_id = c.id AND p.deleted_at IS NULL AND p.is_active = true
            WHERE c.tenant_id = :tenantId
            GROUP BY c.id
            """, nativeQuery = true)
    List<ShopCategoryRow> findShopPresentation(@Param("tenantId") UUID tenantId);
}

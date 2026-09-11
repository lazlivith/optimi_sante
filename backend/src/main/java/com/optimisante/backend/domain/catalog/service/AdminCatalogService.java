package com.optimisante.backend.domain.catalog.service;

import com.optimisante.backend.config.tenant.TenantContext;
import com.optimisante.backend.domain.catalog.dto.AdminCategoryDto;
import com.optimisante.backend.domain.catalog.dto.TrainingLookupDto;
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
    private final com.optimisante.backend.domain.training.repository.TrainingRepository trainingRepository;
    private final TenantRepository tenantRepository;

    @Transactional(readOnly = true)
    /**
     * Liste filtrée du catalogue. Les filtres vides sont normalisés en {@code null} : une
     * chaîne vide envoyée par un champ de recherche que l'utilisateur vient de vider doit
     * signifier « pas de filtre », pas « chercher la chaîne vide ».
     */
    public Page<AdminProductResponseDto> listProducts(String search,
                                                      UUID categoryId,
                                                      String activeState,
                                                      boolean lowStock,
                                                      String sortBy,
                                                      Pageable pageable) {
        // Requête native (voir ProductRepository.searchForAdmin) : contourne volontairement
        // @SQLRestriction("deleted_at IS NULL AND is_active = true") pour que l'admin voie
        // aussi les produits désactivés (et puisse les réactiver).
        Page<AdminProductRow> rows = productRepository.searchForAdmin(
                blankToNull(search),
                categoryId != null ? categoryId.toString() : null,
                blankToNull(activeState),
                lowStock,
                whitelistSort(sortBy),
                pageable);

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

        var formation = resolveTraining(dto.trainingId(), null);

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
                .training(formation)
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
        // Le rattachement se pose ET se retire : `null` signifie « plus de formation liée »,
        // contrairement à la catégorie, où null veut dire « ne change rien ». La différence est
        // voulue — sans elle, une offre liée posée par erreur serait indéfaisable.
        product.setTraining(resolveTraining(dto.trainingId(), product.getId()));

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
                .trainingId(p.getTraining() != null ? p.getTraining().getId() : null)
                .build();
    }

    /**
     * Convertit une date remontee par une requete native vers le type du contrat public.
     *
     * <p>UTC et non le fuseau du serveur : un {@code Instant} ne porte aucun decalage, en
     * inventer un depuis l'horloge de la machine ferait varier la date affichee selon l'endroit
     * ou tourne le conteneur.</p>
     */
    private static java.time.OffsetDateTime enOffset(java.time.Instant instant) {
        return instant == null ? null : instant.atOffset(java.time.ZoneOffset.UTC);
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
                .promoStartsAt(enOffset(row.getPromoStartsAt()))
                .promoEndsAt(enOffset(row.getPromoEndsAt()))
                .trainingId(row.getTrainingId())
                .build();
    }

    /**
     * Catégories proposées comme filtre, avec leur nombre de produits.
     *
     * <p>Le compteur accompagne chaque entrée parce que le catalogue comporte une part
     * importante de catégories sans aucun produit : sans ce chiffre, l'administrateur en
     * choisit une et obtient une liste vide sans savoir si c'est le filtre ou les données qui
     * sont en cause.</p>
     */
    public java.util.List<AdminCategoryDto> listCategoriesWithCounts() {
        return categoryRepository.findAllWithProductCount(requireTenantId()).stream()
                .map(row -> new AdminCategoryDto(
                        row.getId(), row.getName(), row.getSlug(), row.getProductCount()))
                .toList();
    }

    /**
     * N'accepte que les tris connus. Une valeur inattendue est ignorée au lieu d'être
     * refusée : le tri est un confort d'affichage, pas une donnée — renvoyer une erreur
     * priverait l'utilisateur de sa liste pour une virgule mal placée dans l'URL.
     */
    private static String whitelistSort(String sortBy) {
        String value = blankToNull(sortBy);
        return switch (value == null ? "" : value) {
            case "name_asc", "price_asc", "price_desc" -> value;
            default -> null;
        };
    }

    /**
     * Résout la formation à rattacher, et refuse **avec un message lisible** si elle accompagne
     * déjà un autre équipement.
     *
     * <p>L'index unique {@code uq_products_training} (V38) fait autorité — lui seul tient si
     * deux administrateurs enregistrent en même temps. Mais sa violation remonte telle quelle :
     * « duplicate key value violates unique constraint ». Ce contrôle existe uniquement pour
     * que l'utilisateur lise une phrase, pas une erreur PostgreSQL.</p>
     *
     * @param productId produit en cours d'édition, exclu du contrôle — se rattacher à la
     *                  formation qu'on porte déjà n'est pas un doublon. {@code null} à la
     *                  création.
     */
    private com.optimisante.backend.domain.training.entity.Training resolveTraining(UUID trainingId, UUID productId) {
        if (trainingId == null) {
            return null;
        }
        var formation = trainingRepository.findById(trainingId)
                .orElseThrow(() -> new IllegalArgumentException("Formation introuvable : " + trainingId));

        productRepository.findProductIdLinkedToTraining(trainingId).ifPresent(dejaLie -> {
            if (!dejaLie.equals(productId)) {
                throw new IllegalStateException(
                        "La formation « " + formation.getTitle() + " » accompagne déjà un autre "
                        + "équipement. Retirez-la de ce produit avant de la rattacher ici.");
            }
        });
        return formation;
    }

    /**
     * Formations proposées au rattachement d'un produit.
     *
     * <p>Une formation déjà liée à un autre produit reste dans la liste mais porte
     * {@code alreadyLinked} : la masquer laisserait l'utilisateur chercher une formation qu'il
     * sait exister, sans comprendre pourquoi elle a disparu. La relation étant 1:1, l'interface
     * la signale et la base la refuse — deux barrières, un seul message compréhensible.</p>
     */
    @Transactional(readOnly = true)
    public java.util.List<TrainingLookupDto> listTrainingsForLinking() {
        java.util.Set<UUID> dejaLiees = productRepository.findLinkedTrainingIds();
        return trainingRepository.findAll().stream()
                .filter(t -> Boolean.TRUE.equals(t.getIsPublished()))
                .filter(t -> t.getApprovalStatus() != null && "APPROVED".equals(t.getApprovalStatus().name()))
                .sorted(java.util.Comparator.comparing(t -> t.getTitle() == null ? "" : t.getTitle()))
                .map(t -> new TrainingLookupDto(
                        t.getId(), t.getTitle(),
                        t.getPartnerProfile() != null ? t.getPartnerProfile().getInstitutionName() : null,
                        t.getPrice(), t.getDurationDays(),
                        dejaLiees.contains(t.getId())))
                .toList();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

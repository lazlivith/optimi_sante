package com.optimisante.backend.domain.catalog.controller;

import com.optimisante.backend.domain.catalog.dto.CategoryResponseDto;
import com.optimisante.backend.domain.catalog.dto.ProductResponseDto;
import com.optimisante.backend.domain.catalog.service.CatalogService;
import lombok.RequiredArgsConstructor;
import java.util.Map;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/catalog")
@RequiredArgsConstructor
public class CatalogResource {

    private final CatalogService catalogService;

    @GetMapping("/categories")
    public ResponseEntity<List<CategoryResponseDto>> getCategories() {
        return ResponseEntity.ok(catalogService.getCategories());
    }

    /**
     * Les seuls tris acceptés, et les colonnes qu'ils désignent.
     *
     * <p><b>Une liste FERMÉE, et c'est une protection.</b> {@code Pageable} laisse passer
     * n'importe quel nom de champ dans {@code sort=} ; Hibernate le traduit en SQL. Un nom
     * inconnu fait une erreur 500, et un nom d'association permet de trier sur des tables que
     * la route n'expose pas. Ici, ce que le client demande est traduit par NOUS.</p>
     *
     * <p>« Pertinence » n'y figure pas : c'est l'ordre naturel du dépôt, obtenu en ne triant
     * pas. Le demander explicitement revient à ne rien passer.</p>
     */
    private static final Map<String, Sort> TRIS = Map.of(
            "prix-croissant",  Sort.by(Sort.Direction.ASC, "basePrice"),
            "prix-decroissant", Sort.by(Sort.Direction.DESC, "basePrice"),
            "nouveautes",      Sort.by(Sort.Direction.DESC, "createdAt"));

    @GetMapping("/products")
    public ResponseEntity<Page<ProductResponseDto>> searchProducts(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            /** Alimente la page « Promotions » et le carrousel d'accueil. */
            @RequestParam(required = false) Boolean promo,
            /** Vrai : seulement les produits sur devis. Faux : seulement ceux vendus en ligne. */
            @RequestParam(required = false) Boolean surDevis,
            /** Une des clés de {@link #TRIS} ; absente ou « pertinence » laisse l'ordre naturel. */
            @RequestParam(required = false) String tri,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        verifierPrix(minPrice, maxPrice);
        return ResponseEntity.ok(catalogService.searchProducts(
                search, categoryId, minPrice, maxPrice, promo, surDevis, avecTri(pageable, tri)));
    }

    /**
     * Un prix négatif ou un intervalle inversé est une erreur de l'appelant, pas une liste vide.
     *
     * <p>Rendre zéro résultat laisserait croire que le rayon ne contient rien dans cette
     * tranche, alors que la question posée n'avait pas de sens. Le 400 dit laquelle.</p>
     */
    private static void verifierPrix(BigDecimal min, BigDecimal max) {
        if (min != null && min.signum() < 0) {
            throw new IllegalArgumentException("Le prix minimum ne peut pas être négatif.");
        }
        if (max != null && max.signum() < 0) {
            throw new IllegalArgumentException("Le prix maximum ne peut pas être négatif.");
        }
        if (min != null && max != null && min.compareTo(max) > 0) {
            throw new IllegalArgumentException(
                    "Le prix minimum ne peut pas dépasser le prix maximum.");
        }
    }

    private static Pageable avecTri(Pageable pageable, String tri) {
        if (tri == null || tri.isBlank() || "pertinence".equals(tri)) {
            return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        }
        Sort ordre = TRIS.get(tri);
        if (ordre == null) {
            throw new IllegalArgumentException("Tri inconnu : « " + tri + " ».");
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), ordre);
    }

    @GetMapping("/products/{slug}")
    public ResponseEntity<ProductResponseDto> getProductBySlug(@PathVariable String slug) {
        return ResponseEntity.ok(catalogService.getProductBySlug(slug));
    }
}

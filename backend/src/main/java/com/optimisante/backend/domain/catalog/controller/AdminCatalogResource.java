package com.optimisante.backend.domain.catalog.controller;

import com.optimisante.backend.domain.catalog.dto.AdminCategoryDto;
import com.optimisante.backend.domain.catalog.dto.AdminProductRequestDto;
import com.optimisante.backend.domain.catalog.dto.AdminProductResponseDto;
import com.optimisante.backend.domain.catalog.service.AdminCatalogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import com.optimisante.backend.config.security.EcommerceAdmin;

@RestController
@RequestMapping("/api/v1/admin/catalog")
@RequiredArgsConstructor
@EcommerceAdmin
public class AdminCatalogResource {

    private final AdminCatalogService adminCatalogService;

    /**
     * Liste filtrée du catalogue.
     *
     * <p>Tous les filtres sont facultatifs et se combinent. Absents, la réponse est celle
     * d'avant l'ajout de la recherche : le catalogue entier, paginé.</p>
     *
     * @param search      terme cherché dans le nom ou la référence du produit
     * @param categoryId  restreint à une catégorie
     * @param activeState {@code ACTIVE} ou {@code INACTIVE} ; absent, les deux
     * @param lowStock    ne remonte que les produits sous leur seuil de réapprovisionnement
     * @param sortBy      {@code name_asc}, {@code price_asc} ou {@code price_desc}
     *
     * <p><b>Pourquoi {@code sortBy} et non {@code sort}.</b> Spring lit lui-même le paramètre
     * de requête {@code sort} pour alimenter le {@link Pageable}, et ajoute l'{@code ORDER BY}
     * correspondant à la fin de la requête. Nommer notre tri {@code sort} produisait donc deux
     * clauses {@code ORDER BY} concaténées et une erreur de syntaxe SQL — visible seulement à
     * l'exécution, et uniquement lorsqu'un tri était demandé.</p>
     */
    @GetMapping("/products")
    public ResponseEntity<Page<AdminProductResponseDto>> listProducts(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) String activeState,
            @RequestParam(defaultValue = "false") boolean lowStock,
            @RequestParam(required = false) String sortBy,
            @PageableDefault(size = 20) Pageable pageable) {
        // Seules la page et sa taille sont retenues : un tri fourni par le client serait
        // recopié tel quel dans l'ORDER BY de la requête native. Le tri passe exclusivement
        // par `sortBy`, dont les valeurs sont validées par une liste blanche.
        Pageable unsorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        return ResponseEntity.ok(adminCatalogService.listProducts(
                search, categoryId, activeState, lowStock, sortBy, unsorted));
    }

    @PostMapping("/products")
    public ResponseEntity<AdminProductResponseDto> createProduct(@Valid @RequestBody AdminProductRequestDto request) {
        return ResponseEntity.ok(adminCatalogService.createProduct(request));
    }

    @PutMapping("/products/{id}")
    public ResponseEntity<AdminProductResponseDto> updateProduct(
            @PathVariable UUID id,
            @Valid @RequestBody AdminProductRequestDto request) {
        return ResponseEntity.ok(adminCatalogService.updateProduct(id, request));
    }

    @PatchMapping("/products/{id}/status")
    public ResponseEntity<AdminProductResponseDto> setProductActive(
            @PathVariable UUID id,
            @RequestParam boolean active) {
        return ResponseEntity.ok(adminCatalogService.setProductActive(id, active));
    }

    /**
     * Catégories du filtre, avec leur nombre de produits.
     *
     * <p>Charge utile distincte de celle de la boutique ({@code CategoryResponseDto}, qui
     * porte l'arborescence) : l'administration a besoin du compteur, la boutique n'a que
     * faire de savoir combien de produits une catégorie contient.</p>
     */
    @GetMapping("/categories")
    public ResponseEntity<List<AdminCategoryDto>> listCategories() {
        return ResponseEntity.ok(adminCatalogService.listCategoriesWithCounts());
    }
}

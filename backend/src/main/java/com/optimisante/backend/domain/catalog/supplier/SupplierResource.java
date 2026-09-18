package com.optimisante.backend.domain.catalog.supplier;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.optimisante.backend.config.security.EcommerceAdmin;
import com.optimisante.backend.domain.catalog.supplier.SupplierDtos.ImportView;
import com.optimisante.backend.domain.catalog.supplier.SupplierDtos.SupplierRequest;
import com.optimisante.backend.domain.catalog.supplier.SupplierDtos.SupplierView;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Fournisseurs et imports de catalogue, dans l'espace d'administration Négoce.
 *
 * <p>Même périmètre que le catalogue lui-même ({@link EcommerceAdmin}) : qui gère les produits
 * gère leurs fournisseurs. L'administration de la mobilité n'a rien à y faire.</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/suppliers")
@RequiredArgsConstructor
public class SupplierResource {

    /**
     * En-tête du modèle remis aux fournisseurs. Les noms correspondent à ceux que
     * {@link FichierCatalogue} reconnaît en premier ; d'autres intitulés usuels sont acceptés.
     */
    private static final String MODELE_CSV = """
            sku;nom;description;prix;stock;categorie;image_urls
            PROD-001;Tensiomètre bras Omron;Tensiomètre électronique, brassard 22-42 cm;45000;15;Matériel médical;https://exemple.fr/tensiometre.jpg
            PROD-002;Boîte de gants nitrile (100);Gants d'examen non poudrés, taille M;9500;240;Consommables;https://exemple.fr/gants-1.jpg|https://exemple.fr/gants-2.jpg
            """;

    private final SupplierService supplierService;
    private final CatalogImportService importService;

    /**
     * Sérialiseur construit ici : ce projet n'expose pas de bean {@code ObjectMapper} (même
     * constat que {@code AiService}, où l'injection faisait échouer le démarrage).
     */
    private static final ObjectMapper JSON = new ObjectMapper();

    // ------------------------------------------------------------------ FOURNISSEURS ----

    @GetMapping
    @EcommerceAdmin
    public ResponseEntity<List<SupplierView>> lister() {
        return ResponseEntity.ok(supplierService.lister());
    }

    @GetMapping("/{id}")
    @EcommerceAdmin
    public ResponseEntity<SupplierView> detail(@PathVariable UUID id) {
        return ResponseEntity.ok(supplierService.parId(id));
    }

    @PostMapping
    @EcommerceAdmin
    public ResponseEntity<SupplierView> creer(@Valid @RequestBody SupplierRequest dto, Authentication auth) {
        return ResponseEntity.ok(supplierService.creer(dto, utilisateur(auth)));
    }

    @PutMapping("/{id}")
    @EcommerceAdmin
    public ResponseEntity<SupplierView> modifier(@PathVariable UUID id, @Valid @RequestBody SupplierRequest dto) {
        return ResponseEntity.ok(supplierService.modifier(id, dto));
    }

    @PatchMapping("/{id}/status")
    @EcommerceAdmin
    public ResponseEntity<SupplierView> changerActivite(@PathVariable UUID id, @RequestParam boolean active) {
        return ResponseEntity.ok(supplierService.changerActivite(id, active));
    }

    // ----------------------------------------------------------------------- IMPORTS ----

    /** Modèle à envoyer au fournisseur : les colonnes attendues, avec deux lignes d'exemple. */
    @GetMapping(value = "/import-template.csv", produces = "text/csv")
    @EcommerceAdmin
    public ResponseEntity<byte[]> modele() {
        // BOM en tête : sans lui, Excel ouvre le fichier en latin-1 et affiche « Tensiomètre ».
        byte[] contenu = ("﻿" + MODELE_CSV).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"modele-catalogue-optimi-sante.csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(contenu);
    }

    /** Dépose et analyse un fichier : rien n'est écrit au catalogue à ce stade. */
    @PostMapping("/{id}/imports")
    @EcommerceAdmin
    public ResponseEntity<ImportView> analyser(@PathVariable UUID id,
                                               @RequestParam("file") MultipartFile file,
                                               Authentication auth) {
        return ResponseEntity.ok(vue(importService.analyser(id, file, utilisateur(auth))));
    }

    @GetMapping("/{id}/imports")
    @EcommerceAdmin
    public ResponseEntity<List<ImportView>> historique(@PathVariable UUID id) {
        return ResponseEntity.ok(importService.historique(id).stream().map(this::vue).toList());
    }

    @GetMapping("/imports/{importId}")
    @EcommerceAdmin
    public ResponseEntity<ImportView> suivre(@PathVariable UUID importId) {
        return ResponseEntity.ok(vue(importService.parId(importId)));
    }

    @PostMapping("/imports/{importId}/confirm")
    @EcommerceAdmin
    public ResponseEntity<ImportView> confirmer(@PathVariable UUID importId) {
        return ResponseEntity.ok(vue(importService.confirmer(importId)));
    }

    @PostMapping("/imports/{importId}/cancel")
    @EcommerceAdmin
    public ResponseEntity<ImportView> annuler(@PathVariable UUID importId) {
        return ResponseEntity.ok(vue(importService.annuler(importId)));
    }

    // ------------------------------------------------------------------------ OUTILS ----

    private ImportView vue(CatalogImport i) {
        List<String> motifs = new ArrayList<>();
        boolean tronque = false;
        if (i.getReport() != null) {
            try {
                Map<?, ?> rapport = JSON.readValue(i.getReport(), Map.class);
                Object lignes = rapport.get("motifs");
                if (lignes instanceof List<?> liste) {
                    liste.forEach(m -> motifs.add(String.valueOf(m)));
                }
                tronque = Boolean.TRUE.equals(rapport.get("tronque"));
            } catch (Exception e) {
                log.warn("Rapport d'import {} illisible : {}", i.getId(), e.getMessage());
            }
        }
        return new ImportView(i.getId(), i.getSupplier().getId(), i.getFileName(), i.getStatus().name(),
                i.getTotalRows(), i.getToCreate(), i.getToUpdate(), i.getIgnoredRows(), i.getErrorRows(),
                i.getProcessedRows(), i.getCreatedCount(), i.getUpdatedCount(), i.getImageCount(),
                motifs, tronque, i.getFailureReason(),
                i.getCreatedAt(), i.getConfirmedAt(), i.getFinishedAt());
    }

    private static UUID utilisateur(Authentication auth) {
        return UUID.fromString(auth.getPrincipal().toString());
    }
}

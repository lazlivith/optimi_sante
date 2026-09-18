package com.optimisante.backend.domain.catalog.supplier;

import com.optimisante.backend.domain.identity.entity.Tenant;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Un dépôt de fichier catalogue, de son analyse à son résultat.
 *
 * <p><b>Deux temps, un seul enregistrement.</b> Le dépôt analyse le fichier sans rien écrire
 * ({@code ANALYSE} → {@code PRET}) et dit ce qu'il ferait ; la confirmation applique
 * ({@code IMPORT} → {@code TERMINE}). Deux mille lignes mal formées ne peuvent donc pas entrer au
 * catalogue par inadvertance, et les compteurs annoncés sont ceux qui seront tenus.</p>
 *
 * <p>Le fichier lui-même est conservé au stockage : la confirmation le relit plutôt que de garder
 * deux mille lignes en mémoire entre les deux étapes, et un import contesté peut être rejoué.</p>
 */
@Entity
@Table(name = "catalog_imports")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CatalogImport {

    /** Avancement d'un import. ⚠️ À garder aligné sur {@code catalog_imports_status_check} (V58). */
    public enum Statut {
        /** Fichier reçu, analyse en cours. */
        ANALYSE,
        /** Analysé : l'écran montre ce qui serait créé, mis à jour, ignoré ou refusé. */
        PRET,
        /** Confirmé : écriture en cours, l'avancement se lit sur {@code processedRows}. */
        IMPORT,
        TERMINE,
        ECHEC,
        /** Abandonné avant confirmation : rien n'a été écrit. */
        ANNULE
    }

    @Id
    @GeneratedValue(generator = "uuid2")
    @UuidGenerator
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "storage_key", nullable = false, length = 255)
    private String storageKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Statut status;

    // ---- Ce que l'analyse annonce ----
    @Column(name = "total_rows", nullable = false)
    @Builder.Default
    private Integer totalRows = 0;

    @Column(name = "to_create", nullable = false)
    @Builder.Default
    private Integer toCreate = 0;

    @Column(name = "to_update", nullable = false)
    @Builder.Default
    private Integer toUpdate = 0;

    /** Lignes valides mais écartées : SKU appartenant à un autre fournisseur, produit désactivé… */
    @Column(name = "ignored_rows", nullable = false)
    @Builder.Default
    private Integer ignoredRows = 0;

    /** Lignes refusées : colonne obligatoire vide, prix illisible… */
    @Column(name = "error_rows", nullable = false)
    @Builder.Default
    private Integer errorRows = 0;

    // ---- Ce que l'import a réellement fait ----
    @Column(name = "processed_rows", nullable = false)
    @Builder.Default
    private Integer processedRows = 0;

    @Column(name = "created_count", nullable = false)
    @Builder.Default
    private Integer createdCount = 0;

    @Column(name = "updated_count", nullable = false)
    @Builder.Default
    private Integer updatedCount = 0;

    @Column(name = "image_count", nullable = false)
    @Builder.Default
    private Integer imageCount = 0;

    /** Lignes refusées ou écartées, en JSON. Lu tel quel par l'écran, jamais interrogé en SQL. */
    @Column(columnDefinition = "TEXT")
    private String report;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "confirmed_at")
    private OffsetDateTime confirmedAt;

    @Column(name = "finished_at")
    private OffsetDateTime finishedAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}

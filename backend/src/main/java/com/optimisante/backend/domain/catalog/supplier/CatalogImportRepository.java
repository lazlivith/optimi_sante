package com.optimisante.backend.domain.catalog.supplier;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CatalogImportRepository extends JpaRepository<CatalogImport, UUID> {

    List<CatalogImport> findBySupplierIdOrderByCreatedAtDesc(UUID supplierId);

    boolean existsBySupplierIdAndStatus(UUID supplierId, CatalogImport.Statut status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM CatalogImport i WHERE i.id = :id")
    Optional<CatalogImport> findByIdForUpdate(@Param("id") UUID id);

    /**
     * Avancement écrit hors du fil de traitement, pour que l'écran le voie pendant l'import.
     *
     * <p>Une écriture directe, sans charger l'entité : le traitement, lui, tient une transaction
     * par lot de lignes, et rien ne doit dépendre de son rythme.</p>
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE CatalogImport i
               SET i.processedRows = :traitees, i.createdCount = :crees,
                   i.updatedCount = :maj, i.imageCount = :images
             WHERE i.id = :id
            """)
    void majAvancement(@Param("id") UUID id, @Param("traitees") int traitees, @Param("crees") int crees,
                       @Param("maj") int maj, @Param("images") int images);
}

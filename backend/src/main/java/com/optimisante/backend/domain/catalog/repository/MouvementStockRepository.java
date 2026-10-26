package com.optimisante.backend.domain.catalog.repository;

import com.optimisante.backend.domain.catalog.entity.MouvementStock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface MouvementStockRepository extends JpaRepository<MouvementStock, UUID> {

    @Query("SELECT m FROM MouvementStock m WHERE m.product.id = :productId ORDER BY m.createdAt DESC")
    Page<MouvementStock> historique(@Param("productId") UUID productId, Pageable pageable);

    @Query("SELECT m FROM MouvementStock m ORDER BY m.createdAt DESC")
    Page<MouvementStock> journal(Pageable pageable);
}

package com.optimisante.backend.domain.catalog.repository;

import com.optimisante.backend.domain.catalog.entity.StockReservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface StockReservationRepository extends JpaRepository<StockReservation, UUID> {

    @Query("SELECT COALESCE(SUM(sr.quantity), 0) FROM StockReservation sr WHERE sr.product.id = :productId AND sr.expiresAt > CURRENT_TIMESTAMP")
    Integer sumActiveReservationsByProductId(@Param("productId") UUID productId);

    List<StockReservation> findByUserIdAndProductId(UUID userId, UUID productId);

    void deleteByExpiresAtBefore(OffsetDateTime time);
}

package com.optimisante.backend.domain.promotion.repository;

import com.optimisante.backend.domain.promotion.entity.PromoCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PromoCodeRepository extends JpaRepository<PromoCode, UUID> {

    Optional<PromoCode> findByTenantIdAndCodeIgnoreCase(UUID tenantId, String code);

    List<PromoCode> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    boolean existsByTenantIdAndCodeIgnoreCase(UUID tenantId, String code);

    // Réservation atomique de l'usage, même principe que la décrémentation des places de
    // session : évite une double consommation en cas de requêtes concurrentes sur un code à
    // usage limité.
    @Modifying
    @Query("UPDATE PromoCode p SET p.usedCount = p.usedCount + 1 " +
           "WHERE p.id = :id AND (p.maxUses IS NULL OR p.usedCount < p.maxUses)")
    int incrementUsage(@Param("id") UUID id);
}

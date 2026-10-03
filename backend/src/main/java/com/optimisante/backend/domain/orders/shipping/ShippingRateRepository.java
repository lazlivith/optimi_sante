package com.optimisante.backend.domain.orders.shipping;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ShippingRateRepository extends JpaRepository<ShippingRate, UUID> {

    List<ShippingRate> findByTenantIdOrderByZoneAsc(UUID tenantId);

    Optional<ShippingRate> findByTenantIdAndZone(UUID tenantId, ZoneLivraison zone);
}

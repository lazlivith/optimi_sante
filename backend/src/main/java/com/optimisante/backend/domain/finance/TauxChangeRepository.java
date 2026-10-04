package com.optimisante.backend.domain.finance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TauxChangeRepository extends JpaRepository<TauxChange, UUID> {

    List<TauxChange> findByTenantIdOrderByDeviseAsc(UUID tenantId);

    List<TauxChange> findByTenantIdAndActifTrueOrderByDeviseAsc(UUID tenantId);

    Optional<TauxChange> findByTenantIdAndDevise(UUID tenantId, String devise);
}

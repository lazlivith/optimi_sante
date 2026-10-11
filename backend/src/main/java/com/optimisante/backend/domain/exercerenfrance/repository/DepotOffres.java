package com.optimisante.backend.domain.exercerenfrance.repository;

import com.optimisante.backend.domain.exercerenfrance.entity.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DepotOffres extends JpaRepository<EvcContenu.Offre, UUID> {
    List<EvcContenu.Offre> findByTenantIdOrderByOrdreAsc(UUID tenantId);

    /** Ce que « Nos accompagnements » affiche : actives, et non techniques. */
    List<EvcContenu.Offre> findByTenantIdAndActifTrueAndTechniqueFalseOrderByOrdreAsc(UUID tenantId);

    Optional<EvcContenu.Offre> findByTenantIdAndCle(UUID tenantId, String cle);
}

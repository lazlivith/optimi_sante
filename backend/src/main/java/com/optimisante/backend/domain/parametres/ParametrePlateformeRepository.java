package com.optimisante.backend.domain.parametres;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ParametrePlateformeRepository
        extends JpaRepository<ParametrePlateforme, ParametrePlateforme.Cle> {

    Optional<ParametrePlateforme> findByTenantIdAndCle(UUID tenantId, String cle);
}

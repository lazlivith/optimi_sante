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

public interface DepotDemandes extends JpaRepository<AccompanimentRequest, UUID> {

    Page<AccompanimentRequest> findByTenantId(UUID tenantId, Pageable pageable);

    long countByTenantIdAndStatut(UUID tenantId, String statut);

    long countByTenantIdAndCreeLeAfter(UUID tenantId, OffsetDateTime depuis);

    /**
     * Combien de demandes cette adresse a déposées depuis cette date.
     *
     * <p>Le garde-fou anti-abus se lit en base, et non dans un compteur en mémoire :
     * l'instance est petite et peut redémarrer, un compteur mémoire repartirait à zéro
     * — et tiendrait autant de lignes que d'adresses croisées.</p>
     */
    long countByEmailIgnoreCaseAndCreeLeAfter(String email, OffsetDateTime depuis);

    /** Lot de demandes dont la conservation est échue. La tâche d'anonymisation les prend par paquets. */
    @Query("""
           SELECT d FROM AccompanimentRequest d
           WHERE d.anonymiseLe IS NULL
             AND ((d.statut <> 'paye' AND d.derniereActiviteLe < :limiteProspects)
               OR (d.statut = 'paye' AND d.prestationFinLe IS NOT NULL
                   AND d.prestationFinLe < :limiteContrats))
           ORDER BY d.derniereActiviteLe ASC
           """)
    List<AccompanimentRequest> aAnonymiser(@Param("limiteProspects") OffsetDateTime limiteProspects,
                                           @Param("limiteContrats") OffsetDateTime limiteContrats,
                                           Pageable lot);
}

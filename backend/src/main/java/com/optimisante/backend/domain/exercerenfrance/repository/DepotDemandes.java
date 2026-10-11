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

    /**
     * Lot de demandes dont la conservation est échue.
     *
     * <p><b>Le marqueur du régime long est la DATE DE FIN DE PRESTATION, et non le statut.</b>
     * Une demande réglée avance ensuite vers « en cours » puis « terminé » : elle quitte donc
     * le statut « payé ». Fondée sur lui, la règle la faisait retomber parmi les prospects, et
     * une pièce contractuelle aurait été effacée vingt-quatre mois après la dernière activité
     * au lieu de cinq ans après la fin de la prestation — bien avant la fin de la prescription
     * commerciale. La date, elle, ne s'efface pas quand le statut avance.</p>
     *
     * <p>Une demande réglée dont la fin de prestation n'est pas renseignée n'est jamais
     * anonymisée : le délai n'a pas commencé. C'est délibéré — conserver vaut mieux
     * qu'effacer une pièce qu'un litige pourrait réclamer — mais c'est aussi un oubli à
     * corriger, que le tableau de bord signale ({@link #compterPrestationsSansFin}).</p>
     */
    @Query("""
           SELECT d FROM AccompanimentRequest d
           WHERE d.anonymiseLe IS NULL
             AND ((d.prestationFinLe IS NULL
                   AND d.statut NOT IN ('paye', 'en_cours', 'termine')
                   AND d.derniereActiviteLe < :limiteProspects)
               OR (d.prestationFinLe IS NOT NULL AND d.prestationFinLe < :limiteContrats))
           ORDER BY d.derniereActiviteLe ASC
           """)
    List<AccompanimentRequest> aAnonymiser(@Param("limiteProspects") OffsetDateTime limiteProspects,
                                           @Param("limiteContrats") OffsetDateTime limiteContrats,
                                           Pageable lot);

    /**
     * Demandes engagées depuis longtemps sans date de fin de prestation.
     *
     * <p>Sans cette date, le délai de conservation ne commence jamais : la demande serait
     * gardée indéfiniment, ce qui est un manquement au même titre qu'un effacement trop
     * précoce. Le tableau de bord les signale pour qu'on les referme.</p>
     */
    @Query("""
           SELECT count(d) FROM AccompanimentRequest d
           WHERE d.tenantId = :tenantId AND d.anonymiseLe IS NULL
             AND d.prestationFinLe IS NULL
             AND d.statut IN ('paye', 'en_cours', 'termine')
             AND d.derniereActiviteLe < :limite
           """)
    long compterPrestationsSansFin(@Param("tenantId") UUID tenantId,
                                   @Param("limite") OffsetDateTime limite);
}

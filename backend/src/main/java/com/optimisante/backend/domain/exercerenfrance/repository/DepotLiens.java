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

public interface DepotLiens extends JpaRepository<EvcContenu.Lien, UUID> {
    List<EvcContenu.Lien> findAllByOrderByCleAsc();

    Optional<EvcContenu.Lien> findByCle(String cle);

    /**
     * Liens jamais vérifiés, ou vérifiés avant cette date.
     *
     * <p>Le {@code IS NULL} compte autant que la comparaison : un lien jamais contrôlé
     * est au moins aussi suspect qu'un lien contrôlé il y a un an.</p>
     */
    @Query("SELECT count(l) FROM EvcLien l "
         + "WHERE l.url <> 'A_COMPLETER' AND (l.verifieLe IS NULL OR l.verifieLe < :limite)")
    long compterNonVerifiesDepuis(@Param("limite") OffsetDateTime limite);
}

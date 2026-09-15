package com.optimisante.backend.domain.training.repository;

import com.optimisante.backend.domain.training.entity.EnrollmentOfficialDocument;
import com.optimisante.backend.domain.training.entity.OfficialDocumentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface EnrollmentOfficialDocumentRepository extends JpaRepository<EnrollmentOfficialDocument, UUID> {

    List<EnrollmentOfficialDocument> findByEnrollmentIdOrderByCreatedAtDesc(UUID enrollmentId);

    /** Coffre-fort du médecin : tous ses dossiers en une requête, et non une par dossier. */
    List<EnrollmentOfficialDocument> findByEnrollmentIdInAndStatusOrderByCreatedAtDesc(
            Collection<UUID> enrollmentIds, OfficialDocumentStatus status);

    /**
     * Sort d'un document encore à vérifier, en une seule instruction.
     *
     * <p>La condition sur le statut rend la vérification atomique : deux administrateurs qui
     * valident et refusent le même document au même instant ne peuvent pas réussir tous les
     * deux — le second modifie zéro ligne. Lire puis écrire laissait les deux passer.</p>
     *
     * @return 1 si le document a été vérifié, 0 s'il ne l'attendait plus
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE EnrollmentOfficialDocument d
               SET d.status = :statut, d.rejectionReason = :motif, d.reviewedBy = :admin, d.reviewedAt = :le
             WHERE d.id = :id AND d.status = com.optimisante.backend.domain.training.entity.OfficialDocumentStatus.PENDING_REVIEW
            """)
    int verifierSiEnAttente(@Param("id") UUID id, @Param("statut") OfficialDocumentStatus statut,
                            @Param("motif") String motif, @Param("admin") UUID admin, @Param("le") OffsetDateTime le);
}

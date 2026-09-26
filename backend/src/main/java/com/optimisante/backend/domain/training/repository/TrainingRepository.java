package com.optimisante.backend.domain.training.repository;

import com.optimisante.backend.domain.training.entity.Training;
import com.optimisante.backend.domain.training.entity.TrainingApprovalStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TrainingRepository extends JpaRepository<Training, UUID> {
    Optional<Training> findBySlug(String slug);
    List<Training> findByIsPublishedTrue();
    List<Training> findByPartnerProfileUserId(UUID partnerUserId);
    List<Training> findByApprovalStatus(TrainingApprovalStatus status);

    // --- Suppression d'une formation -----------------------------------------------------
    //
    // Requêtes natives : elles portent sur des tables (doctor_applications, prospect_leads,
    // training_service_options) que le modèle JPA ne relie pas à Training. Passer par le
    // dialecte SQL est ici plus honnête que de créer des associations qui n'existent que
    // pour être effacées.

    /** Dossiers d'inscription portés par les sessions de cette formation. */
    @Query(value = """
            SELECT count(*) FROM enrollments e
             JOIN training_sessions s ON s.id = e.session_id
            WHERE s.training_id = :formation""", nativeQuery = true)
    long compterDossiers(@Param("formation") UUID formation);

    /** Candidatures déposées sur les sessions de cette formation. */
    @Query(value = """
            SELECT count(*) FROM doctor_applications a
             JOIN training_sessions s ON s.id = a.session_id
            WHERE s.training_id = :formation""", nativeQuery = true)
    long compterCandidatures(@Param("formation") UUID formation);

    /** Le produit survit à la formation : il perd le lien, pas son existence. */
    @Modifying
    @Query(value = "UPDATE products SET training_id = NULL WHERE training_id = :formation",
           nativeQuery = true)
    int delierProduits(@Param("formation") UUID formation);

    /** Un prospect capté reste un prospect, même si la formation disparaît. */
    @Modifying
    @Query(value = "UPDATE prospect_leads SET training_id = NULL WHERE training_id = :formation",
           nativeQuery = true)
    int delierProspects(@Param("formation") UUID formation);

    @Modifying
    @Query(value = "DELETE FROM training_service_options WHERE training_id = :formation",
           nativeQuery = true)
    int supprimerOptions(@Param("formation") UUID formation);

    @Modifying
    @Query(value = "DELETE FROM training_sessions WHERE training_id = :formation",
           nativeQuery = true)
    int supprimerSessions(@Param("formation") UUID formation);
}

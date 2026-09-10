package com.optimisante.backend.domain.training.repository;

import com.optimisante.backend.domain.training.entity.DocumentRequestStatus;
import com.optimisante.backend.domain.training.entity.EnrollmentDocumentRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface EnrollmentDocumentRequestRepository extends JpaRepository<EnrollmentDocumentRequest, UUID> {

    /**
     * Demandes d'un dossier, piece deja deposee chargee d'avance.
     *
     * <p>Le {@code LEFT JOIN FETCH} est indispensable : chaque ligne affiche la piece qui la
     * satisfait, et une demande en attente n'en a pas. Un {@code JOIN FETCH} simple ferait
     * disparaitre precisement les demandes non satisfaites — celles qui appellent une action.</p>
     */
    @Query("""
            SELECT r FROM EnrollmentDocumentRequest r
            LEFT JOIN FETCH r.document
            WHERE r.enrollment.id = :enrollmentId
            ORDER BY r.requestedAt ASC
            """)
    List<EnrollmentDocumentRequest> findByEnrollmentId(@Param("enrollmentId") UUID enrollmentId);

    /**
     * Garde-fou applicatif du doublon, en complement de l'index unique partiel de la V37.
     *
     * <p>La contrainte SQL reste l'autorite : elle tient meme si deux requetes arrivent
     * simultanement. Cette methode existe pour rendre le refus <b>lisible</b> — sans elle,
     * l'utilisateur recevrait une violation de contrainte au lieu d'un message expliquant que
     * la piece est deja reclamee.</p>
     */
    boolean existsByEnrollmentIdAndLabelIgnoreCaseAndStatusIn(
            UUID enrollmentId, String label, Collection<DocumentRequestStatus> statuses);
}

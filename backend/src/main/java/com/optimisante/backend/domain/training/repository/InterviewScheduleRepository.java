package com.optimisante.backend.domain.training.repository;

import com.optimisante.backend.domain.training.entity.InterviewSchedule;
import com.optimisante.backend.domain.training.entity.InterviewScheduleStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InterviewScheduleRepository extends JpaRepository<InterviewSchedule, UUID> {

    /**
     * L'entretien vivant d'un dossier, s'il existe.
     *
     * <p>L'index unique partiel {@code uq_interview_schedule_actif} (V41) garantit qu'il n'y en
     * a qu'un : cette methode peut donc renvoyer un {@code Optional} sans risque de tomber sur
     * plusieurs lignes.</p>
     */
    Optional<InterviewSchedule> findByEnrollmentIdAndStatusNot(
            UUID enrollmentId, InterviewScheduleStatus status);

    /** Historique complet du dossier, entretiens annules compris. */
    List<InterviewSchedule> findByEnrollmentIdOrderByProposedAtDesc(UUID enrollmentId);

    /**
     * File de travail de l'administration : ce qui attend d'etre transmis au medecin.
     *
     * <p>Les associations que la vue affiche sont chargees dans la meme requete. Sans ces
     * jointures, chaque ligne de la file en declenchait cinq de plus — mesure a 16 acces de
     * table pour trois entretiens seulement, et la file grandit avec l'activite. Le
     * {@code DISTINCT} est impose par la jointure sur la collection de creneaux, qui multiplie
     * sinon la ligne parente.</p>
     */
    @Query("""
            SELECT DISTINCT s FROM InterviewSchedule s
              JOIN FETCH s.enrollment e
              JOIN FETCH e.doctor
              JOIN FETCH e.session sess
              JOIN FETCH sess.training t
              JOIN FETCH t.partnerProfile
              LEFT JOIN FETCH s.slots
            WHERE s.status = :status
            ORDER BY s.proposedAt ASC
            """)
    List<InterviewSchedule> findAwaiting(@Param("status") InterviewScheduleStatus status);
}

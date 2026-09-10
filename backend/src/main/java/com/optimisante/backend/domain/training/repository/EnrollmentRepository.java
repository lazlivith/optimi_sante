package com.optimisante.backend.domain.training.repository;

import com.optimisante.backend.domain.training.entity.Enrollment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EnrollmentRepository extends JpaRepository<Enrollment, UUID> {
    List<Enrollment> findByDoctorId(UUID doctorId);
    Optional<Enrollment> findByIdAndDoctorId(UUID id, UUID doctorId);
    boolean existsByDoctorIdAndSessionId(UUID doctorId, UUID sessionId);
    List<Enrollment> findBySessionTrainingPartnerProfileUserId(UUID userId);
    List<Enrollment> findBySessionTrainingPartnerProfileUserIdAndSessionTrainingId(UUID userId, UUID trainingId);

    /**
     * Liste d'administration, associations chargées d'avance.
     *
     * <p>Le mapping vers {@code EnrollmentDetailDto} lit le médecin, la session, la formation
     * et le CHU partenaire de chaque dossier. Sans ces jointures, chaque ligne déclenche ses
     * propres requêtes — le fameux N+1, indolore sur douze dossiers et ruineux sur mille.</p>
     */
    @Query("""
            SELECT DISTINCT e FROM Enrollment e
            JOIN FETCH e.doctor
            JOIN FETCH e.session s
            JOIN FETCH s.training t
            JOIN FETCH t.partnerProfile
            """)
    List<Enrollment> findAllForAdmin();
}

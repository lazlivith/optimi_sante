package com.optimisante.backend.domain.training.repository;

import com.optimisante.backend.domain.training.entity.Enrollment;
import org.springframework.data.jpa.repository.JpaRepository;
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
}

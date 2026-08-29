package com.optimisante.backend.domain.training.repository;

import com.optimisante.backend.domain.training.entity.SessionStatus;
import com.optimisante.backend.domain.training.entity.TrainingSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TrainingSessionRepository extends JpaRepository<TrainingSession, UUID> {
    
    List<TrainingSession> findByTrainingIdAndStatus(UUID trainingId, SessionStatus status);
    List<TrainingSession> findByTrainingPartnerProfileUserId(UUID partnerUserId);
    boolean existsByTrainingId(UUID trainingId);

    @Modifying
    @Query("UPDATE TrainingSession s SET s.availableSeats = s.availableSeats - 1 WHERE s.id = :id AND s.availableSeats > 0")
    int decrementAvailableSeats(@Param("id") UUID id);
}

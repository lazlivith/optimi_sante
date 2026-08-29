package com.optimisante.backend.domain.training.repository;

import com.optimisante.backend.domain.training.entity.Training;
import com.optimisante.backend.domain.training.entity.TrainingApprovalStatus;
import org.springframework.data.jpa.repository.JpaRepository;
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
}

package com.optimisante.backend.domain.training.repository;

import com.optimisante.backend.domain.training.entity.InterviewSlot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface InterviewSlotRepository extends JpaRepository<InterviewSlot, UUID> {
}

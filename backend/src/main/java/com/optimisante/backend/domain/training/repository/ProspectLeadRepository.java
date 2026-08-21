package com.optimisante.backend.domain.training.repository;

import com.optimisante.backend.domain.training.entity.ProspectLead;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ProspectLeadRepository extends JpaRepository<ProspectLead, UUID> {
}

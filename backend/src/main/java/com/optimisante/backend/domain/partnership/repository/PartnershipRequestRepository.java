package com.optimisante.backend.domain.partnership.repository;

import com.optimisante.backend.domain.partnership.entity.PartnershipRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface PartnershipRequestRepository extends JpaRepository<PartnershipRequest, UUID> {
}

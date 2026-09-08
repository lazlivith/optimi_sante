package com.optimisante.backend.domain.training.finance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PartnerPayoutRepository extends JpaRepository<PartnerPayout, UUID> {

    List<PartnerPayout> findByPartnerProfileIdOrderByCreatedAtDesc(UUID partnerProfileId);
}

package com.optimisante.backend.domain.governance.repository;

import com.optimisante.backend.domain.governance.entity.RgpdRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface RgpdRequestRepository extends JpaRepository<RgpdRequest, UUID> {
    Page<RgpdRequest> findAllByOrderByCreatedAtDesc(Pageable pageable);
}

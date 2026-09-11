package com.optimisante.backend.domain.ai.repository;

import com.optimisante.backend.domain.ai.entity.AiDocumentJob;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AiDocumentJobRepository extends JpaRepository<AiDocumentJob, UUID> {

    Page<AiDocumentJob> findAllByOrderByCreatedAtDesc(Pageable pageable);
}

package com.optimisante.backend.domain.training.repository;

import com.optimisante.backend.domain.training.entity.EnrollmentDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface EnrollmentDocumentRepository extends JpaRepository<EnrollmentDocument, UUID> {
    List<EnrollmentDocument> findByEnrollmentId(UUID enrollmentId);
}

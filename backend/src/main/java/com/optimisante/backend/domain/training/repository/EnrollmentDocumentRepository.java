package com.optimisante.backend.domain.training.repository;

import com.optimisante.backend.domain.training.entity.DocumentType;
import com.optimisante.backend.domain.training.entity.EnrollmentDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EnrollmentDocumentRepository extends JpaRepository<EnrollmentDocument, UUID> {
    List<EnrollmentDocument> findByEnrollmentId(UUID enrollmentId);

    /**
     * Piece deja presente pour ce type, s'il y en a une. Sert au redepot : une correction
     * remplace la reference existante au lieu d'ajouter une ligne de plus au coffre-fort.
     */
    Optional<EnrollmentDocument> findFirstByEnrollmentIdAndDocumentType(UUID enrollmentId, DocumentType documentType);
}

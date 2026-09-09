package com.optimisante.backend.domain.training.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Piece reclamee au candidat, suivie individuellement de la demande a l'acceptation.
 *
 * <p>Complete {@link EnrollmentDocument}, qui reste le magasin des fichiers : une demande
 * <i>pointe</i> vers la piece qui la satisfait, elle ne la stocke pas. Un document peut donc
 * exister sans demande — c'est le cas de ceux que l'administration ajoute elle-meme
 * (convention, attestation, courrier consulaire), qui ne sont reclames a personne.</p>
 */
@Entity
@Table(name = "enrollment_document_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EnrollmentDocumentRequest {

    @Id
    @GeneratedValue(generator = "uuid2")
    @UuidGenerator
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "enrollment_id", nullable = false)
    private Enrollment enrollment;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 50)
    private DocumentType documentType;

    /**
     * Ce que le medecin lira. La taxonomie ne suffit pas : {@code OTHER} ne lui apprend pas
     * qu'on attend un acte de naissance traduit.
     */
    @Column(nullable = false, length = 255)
    private String label;

    @Column(columnDefinition = "TEXT")
    private String instructions;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private DocumentRequestStatus status = DocumentRequestStatus.PENDING;

    @Column(name = "due_date")
    private LocalDate dueDate;

    /** Identifiant brut, sans association : un administrateur desactive ne doit pas rendre
     *  l'historique des demandes illisible (meme raison qu'en V25 pour les emails). */
    @Column(name = "requested_by")
    private UUID requestedBy;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private OffsetDateTime requestedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id")
    private EnrollmentDocument document;

    @Column(name = "submitted_at")
    private OffsetDateTime submittedAt;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private OffsetDateTime reviewedAt;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @PrePersist
    protected void onCreate() {
        if (requestedAt == null) {
            requestedAt = OffsetDateTime.now();
        }
    }
}

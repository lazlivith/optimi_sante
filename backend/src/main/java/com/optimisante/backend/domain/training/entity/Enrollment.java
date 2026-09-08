package com.optimisante.backend.domain.training.entity;

import com.optimisante.backend.domain.identity.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "enrollments", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"doctor_id", "session_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Enrollment {

    @Id
    @GeneratedValue(generator = "uuid2")
    @UuidGenerator
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "doctor_id", nullable = false)
    private User doctor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private TrainingSession session;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    /**
     * Statut d'entrée du cycle : l'entité en est la source unique (le service ne le repose
     * plus explicitement à la création). Modèle d'agence — tout dossier commence par la
     * pré-qualification OptimiSanté, quelle que soit la porte d'entrée du médecin
     * (candidature payante ou inscription directe d'un médecin déjà titulaire d'un compte).
     */
    @Builder.Default
    private EnrollmentStatus status = EnrollmentStatus.UNDER_OPTIMI_REVIEW;

    @Column(name = "diploma_url", length = 512)
    private String diplomaUrl;

    @Column(name = "medical_board_registration_url", length = 512)
    private String medicalBoardRegistrationUrl;

    @Column(name = "passport_url", length = 512)
    private String passportUrl;

    @CreationTimestamp
    @Column(name = "submitted_at", updatable = false)
    private OffsetDateTime submittedAt;

    @Column(name = "convention_s3_key", length = 255)
    private String conventionS3Key;

    @Column(name = "attestation_s3_key", length = 255)
    private String attestationS3Key;

    // ---- Traçabilité des décisions (V26) --------------------------------------------

    /** Motif de rejet. La colonne existait depuis V1 mais n'avait jamais été mappée. */
    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "optimi_reviewed_at")
    private OffsetDateTime optimiReviewedAt;

    /** Admin ayant pré-qualifié le dossier. Simple identifiant, sans clé étrangère (V26). */
    @Column(name = "optimi_reviewed_by")
    private UUID optimiReviewedBy;

    @Column(name = "partner_decided_at")
    private OffsetDateTime partnerDecidedAt;

    /** Pièces ou corrections demandées au médecin lors d'un passage en ACTION_REQUIRED. */
    @Column(name = "action_required_note", columnDefinition = "TEXT")
    private String actionRequiredNote;
}

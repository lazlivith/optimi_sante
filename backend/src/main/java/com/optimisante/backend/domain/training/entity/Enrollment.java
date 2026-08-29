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
    @Builder.Default
    private EnrollmentStatus status = EnrollmentStatus.PENDING_REVIEW;

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
}

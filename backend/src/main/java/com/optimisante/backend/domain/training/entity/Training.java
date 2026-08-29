package com.optimisante.backend.domain.training.entity;

import com.optimisante.backend.domain.identity.entity.PartnerProfile;
import com.optimisante.backend.domain.identity.entity.Tenant;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "trainings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Training {

    @Id
    @GeneratedValue(generator = "uuid2")
    @UuidGenerator
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "partner_id", nullable = false)
    private PartnerProfile partnerProfile;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(name = "medical_specialty", nullable = false)
    private String medicalSpecialty;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "brochure_s3_key")
    private String brochureS3Key;

    @Column(name = "duration_days", nullable = false)
    private Integer durationDays;

    @Column(name = "is_long_stay")
    private Boolean isLongStay;

    @Column(nullable = false)
    private BigDecimal price;

    @Column(name = "is_published")
    private Boolean isPublished;

    @Enumerated(EnumType.STRING)
    @Column(name = "approval_status", nullable = false)
    @Builder.Default
    private TrainingApprovalStatus approvalStatus = TrainingApprovalStatus.APPROVED;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "image_s3_key")
    private String imageS3Key;

    @Column(name = "video_s3_key")
    private String videoS3Key;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;
}

package com.optimisante.backend.domain.training.entity;

import com.optimisante.backend.domain.identity.entity.PartnerProfile;
import com.optimisante.backend.domain.identity.entity.Tenant;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
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

    @Column(name = "brochure_s3_key", nullable = false)
    private String brochureS3Key;

    @Column(name = "duration_days", nullable = false)
    private Integer durationDays;

    @Column(name = "is_long_stay")
    private Boolean isLongStay;

    @Column(nullable = false)
    private BigDecimal price;

    @Column(name = "is_published")
    private Boolean isPublished;
}

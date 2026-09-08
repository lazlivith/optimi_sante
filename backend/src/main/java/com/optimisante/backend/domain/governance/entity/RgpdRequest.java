package com.optimisante.backend.domain.governance.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Trace d'une demande RGPD traitée par la gouvernance : export des données d'une personne
 * (droit d'accès / portabilité) ou anonymisation (droit à l'effacement). Historisé pour la
 * démonstration de conformité.
 */
@Entity
@Table(name = "rgpd_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RgpdRequest {

    @Id
    @GeneratedValue(generator = "uuid2")
    @UuidGenerator
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "request_type", nullable = false, length = 20)
    private String requestType; // EXPORT | ANONYMIZE

    @Column(name = "subject_user_id")
    private UUID subjectUserId;

    @Column(name = "subject_email", nullable = false, length = 255)
    private String subjectEmail;

    @Column(nullable = false, length = 20)
    private String status; // DONE | FAILED

    @Column(name = "requested_by_user_id")
    private UUID requestedByUserId;

    @Column(name = "requested_by_email", length = 255)
    private String requestedByEmail;

    @Column(name = "result_summary", columnDefinition = "TEXT")
    private String resultSummary;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}

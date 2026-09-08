package com.optimisante.backend.domain.ai.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Trace d'une extraction documentaire. {@code appliedAt} n'est renseigné que lorsqu'un humain
 * a validé la proposition et l'a reportée dans le dossier — l'IA ne modifie jamais un dossier
 * d'elle-même.
 */
@Entity
@Table(name = "ai_document_jobs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiDocumentJob {

    @Id
    @GeneratedValue(generator = "uuid2")
    @UuidGenerator
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "requested_by")
    private UUID requestedBy;

    @Column(name = "document_type", nullable = false, length = 40)
    private String documentType;

    @Column(name = "file_name", length = 255)
    private String fileName;

    @Column(name = "content_type", length = 120)
    private String contentType;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "type_matches")
    private Boolean typeMatches;

    @Column(name = "extracted_json", columnDefinition = "TEXT")
    private String extractedJson;

    @Column(columnDefinition = "TEXT")
    private String warnings;

    @Column(columnDefinition = "TEXT")
    private String error;

    @Column(length = 80)
    private String model;

    @Column(name = "applied_at")
    private OffsetDateTime appliedAt;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
        if (status == null) {
            status = "SUCCESS";
        }
    }
}

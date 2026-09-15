package com.optimisante.backend.domain.training.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Document remis au médecin : programme ou convention du CHU, pièce du kit de départ.
 *
 * <p><b>Aucun statut de dossier n'est touché.</b> Comme les demandes de pièces (V37) et les
 * entretiens (V41), ces documents se greffent sur la candidature sans la faire avancer.</p>
 *
 * <p><b>Pas de champ « verrouillé ».</b> L'accès du médecin se déduit à la lecture de l'état du
 * dossier et de ses paiements ({@code OfficialDocumentService#accesMedecin}) : un booléen figé
 * au dépôt ne se lèverait jamais quand le paiement arrive ensuite.</p>
 */
@Entity
@Table(name = "enrollment_official_documents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EnrollmentOfficialDocument {

    @Id
    @GeneratedValue(generator = "uuid2")
    @UuidGenerator
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "enrollment_id", nullable = false)
    private Enrollment enrollment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OfficialDocumentCategory category;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(name = "storage_key", nullable = false, length = 255)
    private String storageKey;

    @Column(name = "file_name", length = 255)
    private String fileName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OfficialDocumentIssuer issuer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OfficialDocumentStatus status;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "uploaded_by")
    private UUID uploadedBy;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private OffsetDateTime reviewedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}

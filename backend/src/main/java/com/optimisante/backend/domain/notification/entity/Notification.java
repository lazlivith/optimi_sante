package com.optimisante.backend.domain.notification.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Une notification in-app destinée à un utilisateur précis. Les notifications de rôle
 * (ex. « nouvelle demande de partenariat » pour tous les ADMIN) sont dépliées en une ligne
 * par destinataire au moment de l'écriture ({@code NotificationService.notifyRole}) : chaque
 * ligne porte donc toujours un {@code recipientUserId} et son propre état lu / non lu.
 */
@Entity
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    @GeneratedValue(generator = "uuid2")
    @UuidGenerator
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "recipient_user_id", nullable = false)
    private UUID recipientUserId;

    /** Clé technique du type d'événement (ex. ORDER_PAID, ENROLLMENT_STATUS, PARTNERSHIP_REQUEST). */
    @Column(nullable = false, length = 60)
    private String type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationSeverity severity;

    @Column(nullable = false, length = 180)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String body;

    /** Lien relatif dans l'app (ex. /doctor, /admin/partnership-requests) vers l'écran concerné. */
    @Column(name = "link_url", length = 512)
    private String linkUrl;

    @Column(columnDefinition = "TEXT")
    private String metadata;

    /**
     * Clé de regroupement : deux notifications de même clé non acquittées sont fusionnées
     * ({@code groupCount} incrémenté, la ligne « remonte ») au lieu d'empiler des doublons.
     * {@code null} => pas de déduplication.
     */
    @Column(name = "dedupe_key", length = 120)
    private String dedupeKey;

    @Column(name = "group_count", nullable = false)
    private Integer groupCount;

    @Column(name = "read_at")
    private OffsetDateTime readAt;

    /** « Traité / pris en charge » — distinct de {@link #readAt} (« vu »). Utilisé pour les alertes. */
    @Column(name = "acknowledged_at")
    private OffsetDateTime acknowledgedAt;

    @Column(name = "acknowledged_by")
    private UUID acknowledgedBy;

    /** Mise en veille : la notification est masquée (liste + compteur) jusqu'à cette date. */
    @Column(name = "snoozed_until")
    private OffsetDateTime snoozedUntil;

    /**
     * Horodatage d'apparition. Repoussé au « maintenant » quand la notification est regroupée
     * (déduplication) pour qu'elle remonte en tête de liste — d'où l'absence de {@code updatable = false}.
     */
    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
        if (severity == null) {
            severity = NotificationSeverity.INFO;
        }
        if (groupCount == null) {
            groupCount = 1;
        }
    }
}

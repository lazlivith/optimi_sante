package com.optimisante.backend.domain.notification.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Préférence d'un utilisateur pour un type de notification. <b>Absence de ligne = tout activé</b>
 * (comportement par défaut) ; une ligne permet de couper l'in-app et/ou l'e-mail pour ce type.
 */
@Entity
@Table(name = "notification_preferences")
@IdClass(NotificationPreference.Id.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationPreference {

    @jakarta.persistence.Id
    @Column(name = "user_id")
    private UUID userId;

    @jakarta.persistence.Id
    @Column(length = 60)
    private String type;

    @Column(name = "in_app_enabled", nullable = false)
    private boolean inAppEnabled;

    @Column(name = "email_enabled", nullable = false)
    private boolean emailEnabled;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = OffsetDateTime.now();
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Id implements Serializable {
        private UUID userId;
        private String type;
    }
}

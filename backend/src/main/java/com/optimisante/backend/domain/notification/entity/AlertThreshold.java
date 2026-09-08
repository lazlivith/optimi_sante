package com.optimisante.backend.domain.notification.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

/**
 * Seuil de veille configurable. {@code NotificationAlertJob} lit ces lignes au lieu de constantes
 * en dur : l'admin active/désactive une sonde et ajuste sa fenêtre et sa sévérité sans redéploiement.
 */
@Entity
@Table(name = "alert_thresholds")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlertThreshold {

    @Id
    @Column(length = 60)
    private String key;

    @Column(nullable = false, length = 180)
    private String label;

    @Column(nullable = false)
    private boolean enabled;

    /** Déclenche l'alerte si le nombre observé est supérieur ou égal à cette valeur. */
    @Column(name = "threshold_value", nullable = false)
    private int thresholdValue;

    @Column(name = "window_hours", nullable = false)
    private int windowHours;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationSeverity severity;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = OffsetDateTime.now();
    }
}

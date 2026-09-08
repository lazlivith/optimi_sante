package com.optimisante.backend.domain.notification.entity;

/**
 * Niveau d'une notification — pilote l'icône et la couleur côté front, et sert de filtre
 * pour les alertes opérationnelles admin (WARNING / CRITICAL).
 */
public enum NotificationSeverity {
    INFO,
    SUCCESS,
    WARNING,
    CRITICAL
}

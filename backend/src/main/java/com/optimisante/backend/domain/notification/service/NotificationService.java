package com.optimisante.backend.domain.notification.service;

import com.optimisante.backend.config.tenant.TenantContext;
import com.optimisante.backend.domain.identity.entity.Role;
import com.optimisante.backend.domain.identity.entity.User;
import com.optimisante.backend.domain.identity.repository.UserRepository;
import com.optimisante.backend.domain.notification.entity.Notification;
import com.optimisante.backend.domain.notification.entity.NotificationPreference;
import com.optimisante.backend.domain.notification.entity.NotificationSeverity;
import com.optimisante.backend.domain.notification.repository.NotificationPreferenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Point d'entrée d'écriture des notifications in-app. Deux portées :
 * <ul>
 *   <li>{@link #notifyUser} — un destinataire précis ;</li>
 *   <li>{@link #notifyRole} — tous les utilisateurs d'un rôle (alertes opérationnelles), dépliées
 *       en une ligne par utilisateur pour que l'état lu / acquitté reste individuel.</li>
 * </ul>
 * Avant écriture : respect de la préférence in-app de l'utilisateur pour ce type ; puis
 * déduplication éventuelle sur {@code dedupeKey} (cf. {@link NotificationWriter}).
 * Ne lève jamais : un incident de notification ne casse pas le métier.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    /** Garde-fou : au-delà, un envoi de rôle est tronqué (une plateforme mono-CHU reste très en deçà). */
    private static final int ROLE_FANOUT_CAP = 500;

    private final NotificationWriter notificationWriter;
    private final UserRepository userRepository;
    private final NotificationPreferenceRepository preferenceRepository;

    public void notifyUser(UUID userId, String type, NotificationSeverity severity,
                           String title, String body, String linkUrl, String metadataJson, String dedupeKey) {
        if (userId == null) {
            return;
        }
        try {
            if (!inAppAllowed(userId, type)) {
                return;
            }
            notificationWriter.persistOrGroup(
                    build(userId, type, severity, title, body, linkUrl, metadataJson, dedupeKey));
        } catch (Exception e) {
            log.warn("notifyUser failed (ignored): {}", e.getMessage());
        }
    }

    public void notifyRole(Role role, String type, NotificationSeverity severity,
                           String title, String body, String linkUrl, String metadataJson, String dedupeKey) {
        try {
            for (User u : userRepository.findByRole(role, PageRequest.of(0, ROLE_FANOUT_CAP)).getContent()) {
                if (!inAppAllowed(u.getId(), type)) {
                    continue;
                }
                notificationWriter.persistOrGroup(
                        build(u.getId(), type, severity, title, body, linkUrl, metadataJson, dedupeKey));
            }
        } catch (Exception e) {
            log.warn("notifyRole failed (ignored): {}", e.getMessage());
        }
    }

    /**
     * Notifie l'équipe d'administration, c'est-à-dire {@code ADMIN} <b>et</b> {@code SUPER_ADMIN}.
     *
     * <p>Un {@code notifyRole(Role.ADMIN, ...)} seul est un piège : le rôle {@code ADMIN} peut
     * n'être porté par aucun compte (c'est le cas d'une plateforme pilotée par un unique
     * {@code SUPER_ADMIN}), et l'alerte part alors dans le vide sans la moindre erreur. Toute
     * notification destinée au back-office doit passer par ici.</p>
     */
    public void notifyAdmins(String type, NotificationSeverity severity,
                             String title, String body, String linkUrl, String metadataJson, String dedupeKey) {
        notifyRole(Role.ADMIN, type, severity, title, body, linkUrl, metadataJson, dedupeKey);
        notifyRole(Role.SUPER_ADMIN, type, severity, title, body, linkUrl, metadataJson, dedupeKey);
    }

    /** Adresses e-mail de l'équipe d'administration, pour les alertes qui doivent sortir de l'app. */
    public java.util.List<String> adminEmails() {
        try {
            java.util.List<String> emails = new java.util.ArrayList<>();
            for (Role role : java.util.List.of(Role.ADMIN, Role.SUPER_ADMIN)) {
                userRepository.findByRole(role, PageRequest.of(0, ROLE_FANOUT_CAP)).getContent()
                        .forEach(u -> emails.add(u.getEmail()));
            }
            return emails;
        } catch (Exception e) {
            log.warn("adminEmails failed (ignored): {}", e.getMessage());
            return java.util.List.of();
        }
    }

    /** L'utilisateur accepte-t-il l'e-mail pour ce type ? (Défaut : oui.) Consulté par le dispatcher. */
    public boolean emailAllowed(UUID userId, String type) {
        return preferenceRepository.findById(new NotificationPreference.Id(userId, type))
                .map(NotificationPreference::isEmailEnabled)
                .orElse(true);
    }

    private boolean inAppAllowed(UUID userId, String type) {
        return preferenceRepository.findById(new NotificationPreference.Id(userId, type))
                .map(NotificationPreference::isInAppEnabled)
                .orElse(true);
    }

    private Notification build(UUID userId, String type, NotificationSeverity severity,
                               String title, String body, String linkUrl, String metadataJson, String dedupeKey) {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            tenantId = userRepository.findTenantIdById(userId).orElse(null);
        }
        return Notification.builder()
                .tenantId(tenantId)
                .recipientUserId(userId)
                .type(type)
                .severity(severity == null ? NotificationSeverity.INFO : severity)
                .title(title)
                .body(body)
                .linkUrl(linkUrl)
                .metadata(metadataJson)
                .dedupeKey(dedupeKey)
                .groupCount(1)
                .build();
    }
}

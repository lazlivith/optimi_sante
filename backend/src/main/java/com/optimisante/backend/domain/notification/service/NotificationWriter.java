package com.optimisante.backend.domain.notification.service;

import com.optimisante.backend.domain.notification.entity.Notification;
import com.optimisante.backend.domain.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * Persistance bas niveau des notifications, isolée dans son propre bean pour que la transaction
 * {@code REQUIRES_NEW} soit réellement appliquée par le proxy Spring (même raison que
 * {@code AuditLogWriter}). Une notification qui échoue à s'écrire ne doit jamais annuler ni faire
 * échouer l'action métier qui l'a déclenchée : l'exception est avalée ici.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationWriter {

    private final NotificationRepository notificationRepository;

    /**
     * Écrit une notification, ou <b>la regroupe</b> avec la dernière notification non acquittée
     * de même {@code dedupeKey} pour le même destinataire (incrémente {@code groupCount}, la
     * re-fait remonter non lue). Sans {@code dedupeKey}, insertion simple.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persistOrGroup(Notification candidate) {
        try {
            String key = candidate.getDedupeKey();
            if (key != null && !key.isBlank()) {
                Optional<Notification> existing = notificationRepository
                        .findFirstByRecipientUserIdAndDedupeKeyAndAcknowledgedAtIsNullOrderByCreatedAtDesc(
                                candidate.getRecipientUserId(), key);
                if (existing.isPresent()) {
                    Notification n = existing.get();
                    n.setGroupCount((n.getGroupCount() == null ? 1 : n.getGroupCount()) + 1);
                    n.setTitle(candidate.getTitle());
                    n.setBody(candidate.getBody());
                    n.setSeverity(candidate.getSeverity());
                    n.setLinkUrl(candidate.getLinkUrl());
                    n.setMetadata(candidate.getMetadata());
                    n.setReadAt(null);
                    n.setSnoozedUntil(null);
                    n.setCreatedAt(OffsetDateTime.now());
                    notificationRepository.save(n);
                    return;
                }
            }
            notificationRepository.save(candidate);
        } catch (Exception e) {
            log.warn("Notification write failed (ignored): {}", e.getMessage());
        }
    }
}

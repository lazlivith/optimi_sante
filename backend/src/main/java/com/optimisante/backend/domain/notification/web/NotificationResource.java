package com.optimisante.backend.domain.notification.web;

import com.optimisante.backend.domain.notification.dto.NotificationDto;
import com.optimisante.backend.domain.notification.entity.Notification;
import com.optimisante.backend.domain.notification.entity.NotificationPreference;
import com.optimisante.backend.domain.notification.repository.NotificationPreferenceRepository;
import com.optimisante.backend.domain.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Centre de notifications de l'utilisateur authentifié (tous rôles). Chaque appel est borné au
 * destinataire courant, résolu depuis le SecurityContext (principal JWT = identifiant utilisateur).
 * Les notifications en veille ({@code snoozed_until} dans le futur) sont masquées de la liste et
 * du compteur jusqu'à échéance.
 */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationResource {

    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceRepository preferenceRepository;

    @GetMapping
    public Page<NotificationDto> list(@RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "20") int size,
                                      @RequestParam(defaultValue = "false") boolean unreadOnly) {
        UUID uid = currentUserId();
        OffsetDateTime now = OffsetDateTime.now();
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        Page<Notification> result = unreadOnly
                ? notificationRepository.findVisibleUnread(uid, now, pageable)
                : notificationRepository.findVisible(uid, now, pageable);
        return result.map(NotificationDto::from);
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount() {
        return Map.of("count", notificationRepository.countVisibleUnread(currentUserId(), OffsetDateTime.now()));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Void> markRead(@PathVariable UUID id) {
        mutate(id, n -> {
            if (n.getReadAt() == null) {
                n.setReadAt(OffsetDateTime.now());
            }
        });
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/read-all")
    @Transactional
    public Map<String, Integer> markAllRead() {
        return Map.of("updated", notificationRepository.markAllRead(currentUserId(), OffsetDateTime.now()));
    }

    /** Accusé de réception : « traité ». Marque aussi lu. Sort la notification des alertes ouvertes. */
    @PostMapping("/{id}/ack")
    public ResponseEntity<Void> acknowledge(@PathVariable UUID id) {
        UUID uid = currentUserId();
        mutate(id, n -> {
            OffsetDateTime now = OffsetDateTime.now();
            if (n.getReadAt() == null) {
                n.setReadAt(now);
            }
            n.setAcknowledgedAt(now);
            n.setAcknowledgedBy(uid);
        });
        return ResponseEntity.noContent().build();
    }

    /** Mise en veille : masque la notification pendant {@code hours} (défaut 24, max 720). */
    @PostMapping("/{id}/snooze")
    public ResponseEntity<Void> snooze(@PathVariable UUID id, @RequestParam(defaultValue = "24") int hours) {
        int h = Math.min(Math.max(hours, 1), 720);
        mutate(id, n -> {
            n.setSnoozedUntil(OffsetDateTime.now().plusHours(h));
            if (n.getReadAt() == null) {
                n.setReadAt(OffsetDateTime.now());
            }
        });
        return ResponseEntity.noContent().build();
    }

    // --- Préférences par type -------------------------------------------------

    @GetMapping("/preferences")
    public List<Map<String, Object>> preferences() {
        return preferenceRepository.findByUserId(currentUserId()).stream()
                .map(p -> Map.<String, Object>of(
                        "type", p.getType(),
                        "inAppEnabled", p.isInAppEnabled(),
                        "emailEnabled", p.isEmailEnabled()))
                .toList();
    }

    public record PreferenceRequest(boolean inAppEnabled, boolean emailEnabled) {
    }

    @PutMapping("/preferences/{type}")
    public Map<String, Object> upsertPreference(@PathVariable String type, @RequestBody PreferenceRequest body) {
        UUID uid = currentUserId();
        NotificationPreference pref = preferenceRepository
                .findById(new NotificationPreference.Id(uid, type))
                .orElseGet(() -> NotificationPreference.builder().userId(uid).type(type).build());
        pref.setInAppEnabled(body.inAppEnabled());
        pref.setEmailEnabled(body.emailEnabled());
        preferenceRepository.save(pref);
        return Map.of("type", type, "inAppEnabled", pref.isInAppEnabled(), "emailEnabled", pref.isEmailEnabled());
    }

    // --- helpers ------------------------------------------------------------

    private void mutate(UUID id, java.util.function.Consumer<Notification> change) {
        notificationRepository.findByIdAndRecipientUserId(id, currentUserId()).ifPresent(n -> {
            change.accept(n);
            notificationRepository.save(n);
        });
    }

    private static UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal() == null
                || "anonymousUser".equals(auth.getPrincipal())) {
            throw new IllegalStateException("Utilisateur non authentifié");
        }
        return UUID.fromString(auth.getPrincipal().toString());
    }
}

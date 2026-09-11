package com.optimisante.backend.domain.notification.service;

import com.optimisante.backend.domain.identity.entity.Role;
import com.optimisante.backend.domain.notification.entity.AlertThreshold;
import com.optimisante.backend.domain.notification.repository.AlertThresholdRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Veille par seuils : à intervalle régulier, exécute chaque sonde activée dans
 * {@code alert_thresholds} et pousse une alerte au rôle ADMIN si le compte observé atteint le
 * seuil. La déduplication ({@code dedupeKey} = clé de la sonde) fait que des déclenchements
 * répétés se regroupent sur une seule alerte tant qu'elle n'est pas acquittée. Tout est
 * « fail-soft » — un incident de veille ne perturbe rien.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationAlertJob {

    private final JdbcTemplate jdbc;
    private final AlertThresholdRepository thresholdRepository;
    private final NotificationService notifications;

    /** Toutes les heures — la fenêtre de chaque sonde est portée par sa configuration. */
    @Scheduled(cron = "0 0 * * * *")
    public void runProbes() {
        for (AlertThreshold t : safeEnabledThresholds()) {
            try {
                int count = probe(t.getKey(), t.getWindowHours());
                if (count >= t.getThresholdValue()) {
                    notifications.notifyRole(Role.ADMIN, t.getKey(), t.getSeverity(),
                            t.getLabel(), message(t.getKey(), count, t.getWindowHours()),
                            linkFor(t.getKey()), null, t.getKey());
                }
            } catch (Exception e) {
                log.warn("Alert probe {} failed (ignored): {}", t.getKey(), e.getMessage());
            }
        }
    }

    private java.util.List<AlertThreshold> safeEnabledThresholds() {
        try {
            return thresholdRepository.findByEnabledTrue();
        } catch (Exception e) {
            log.warn("Cannot read alert_thresholds (ignored): {}", e.getMessage());
            return java.util.List.of();
        }
    }

    private int probe(String key, int windowHours) {
        String sql = switch (key) {
            case "REPORT_FAILURE" -> "SELECT COUNT(*) FROM report_runs "
                    + "WHERE status = 'FAILED' AND started_at >= now() - (? || ' hours')::interval";
            case "ORDER_UNPAID_STALE" -> "SELECT COUNT(*) FROM orders "
                    + "WHERE payment_status = 'UNPAID' AND COALESCE(is_quote, false) = false "
                    + "AND created_at < now() - (? || ' hours')::interval";
            case "ENROLLMENT_STALE" -> "SELECT COUNT(*) FROM enrollments "
                    + "WHERE status = 'UNDER_OPTIMI_REVIEW' AND submitted_at < now() - (? || ' hours')::interval";
            default -> null;
        };
        if (sql == null) {
            return 0;
        }
        Integer n = jdbc.queryForObject(sql, Integer.class, windowHours);
        return n == null ? 0 : n;
    }

    private static String message(String key, int count, int windowHours) {
        return switch (key) {
            case "REPORT_FAILURE" ->
                    count + " exécution(s) de rapport ont échoué au cours des dernières " + windowHours + " h.";
            case "ORDER_UNPAID_STALE" ->
                    count + " commande(s) restent non payées depuis plus de " + windowHours + " h.";
            case "ENROLLMENT_STALE" ->
                    count + " dossier(s) CHU attendent un examen depuis plus de " + windowHours + " h.";
            default -> count + " élément(s) à vérifier.";
        };
    }

    private static String linkFor(String key) {
        return switch (key) {
            case "REPORT_FAILURE" -> "/admin/reports";
            case "ORDER_UNPAID_STALE" -> "/admin/orders";
            case "ENROLLMENT_STALE" -> "/admin/enrollments";
            default -> "/admin/alerts";
        };
    }
}

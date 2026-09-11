package com.optimisante.backend.domain.notification.web;

import com.optimisante.backend.config.security.PlatformAdmin;

import com.optimisante.backend.domain.notification.entity.AlertThreshold;
import com.optimisante.backend.domain.notification.entity.NotificationSeverity;
import com.optimisante.backend.domain.notification.repository.AlertThresholdRepository;
import com.optimisante.backend.domain.notification.service.AlertQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Écran admin « Alertes » : liste filtrable (sévérité, statut ouvert/acquitté) des notifications
 * du compte admin courant, et gestion des seuils de veille ({@code alert_thresholds}).
 * L'acquittement / la mise en veille passent par les endpoints génériques {@code /notifications/*}.
 */
/**
 * Perimetre d'acces : supervision (SUPER_ADMIN, ADMIN herite).
 *
 * <p>Les seuils portent sur les deux metiers (commandes impayees, dossiers sans evolution) :
 * transverse par construction.</p>
 */
@RestController
@RequestMapping("/api/v1/admin/alerts")
@RequiredArgsConstructor
@PlatformAdmin
public class AdminAlertResource {

    private static final List<String> ALERT_GRADE = List.of("WARNING", "CRITICAL");

    private final AlertQueryService alertQueryService;
    private final AlertThresholdRepository thresholdRepository;

    @GetMapping
    public Page<Map<String, Object>> list(@RequestParam(required = false) String severity,
                                          @RequestParam(defaultValue = "open") String status,
                                          @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "20") int size) {
        List<String> severities = resolveSeverities(severity);
        return alertQueryService.search(currentUserId(), severities, status, page, size);
    }

    @GetMapping("/thresholds")
    public List<AlertThreshold> thresholds() {
        return thresholdRepository.findAll();
    }

    public record ThresholdUpdate(Boolean enabled, Integer thresholdValue, Integer windowHours, String severity) {
    }

    @PutMapping("/thresholds/{key}")
    public AlertThreshold updateThreshold(@PathVariable String key, @RequestBody ThresholdUpdate body) {
        AlertThreshold t = thresholdRepository.findById(key)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Seuil inconnu : " + key));
        if (body.enabled() != null) {
            t.setEnabled(body.enabled());
        }
        if (body.thresholdValue() != null) {
            t.setThresholdValue(Math.max(body.thresholdValue(), 1));
        }
        if (body.windowHours() != null) {
            t.setWindowHours(Math.min(Math.max(body.windowHours(), 1), 24 * 90));
        }
        if (body.severity() != null && !body.severity().isBlank()) {
            t.setSeverity(NotificationSeverity.valueOf(body.severity().trim().toUpperCase()));
        }
        return thresholdRepository.save(t);
    }

    private static List<String> resolveSeverities(String severity) {
        if (severity == null || severity.isBlank() || "ALERT".equalsIgnoreCase(severity)) {
            return ALERT_GRADE;
        }
        if ("ALL".equalsIgnoreCase(severity)) {
            return List.of();
        }
        return Arrays.stream(severity.split(",")).map(String::trim).map(String::toUpperCase).filter(s -> !s.isBlank()).toList();
    }

    private static UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new IllegalStateException("Utilisateur non authentifié");
        }
        return UUID.fromString(auth.getPrincipal().toString());
    }
}

package com.optimisante.backend.domain.audit.service;

import com.optimisante.backend.config.tenant.TenantContext;
import com.optimisante.backend.domain.audit.entity.AuditLog;
import com.optimisante.backend.domain.identity.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;
import java.util.UUID;

/**
 * Point d'entrée d'écriture du journal d'audit. Construit l'entrée à partir du contexte courant
 * (acteur authentifié, tenant, requête HTTP) puis délègue la persistance à {@link AuditLogWriter}
 * dans une transaction indépendante. Ne lève jamais : un incident d'audit ne casse pas le métier.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogWriter auditLogWriter;
    private final UserRepository userRepository;

    /** Action métier explicite (ex. RGPD_ANONYMIZE, RGPD_EXPORT) déclenchée depuis un service. */
    public void record(String action, String entityType, String entityId, String summary, String metadataJson) {
        try {
            AuditLog entry = baseBuilder()
                    .action(action)
                    .entityType(entityType)
                    .entityId(entityId)
                    .summary(summary)
                    .metadata(metadataJson)
                    .build();
            auditLogWriter.write(entry);
        } catch (Exception e) {
            log.warn("Audit record failed (ignored): {}", e.getMessage());
        }
    }

    /** Écriture générique à partir d'une requête HTTP mutante interceptée. */
    public void recordHttpWrite(String method, String path, int statusCode) {
        try {
            AuditLog entry = baseBuilder()
                    .action("HTTP_WRITE")
                    .httpMethod(method)
                    .path(path)
                    .statusCode(statusCode)
                    .summary(method + " " + path + " -> " + statusCode)
                    .build();
            auditLogWriter.write(entry);
        } catch (Exception e) {
            log.warn("Audit HTTP record failed (ignored): {}", e.getMessage());
        }
    }

    private AuditLog.AuditLogBuilder baseBuilder() {
        AuditLog.AuditLogBuilder builder = AuditLog.builder()
                .tenantId(TenantContext.getTenantId());

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() != null
                && !"anonymousUser".equals(auth.getPrincipal())) {
            String principal = auth.getPrincipal().toString();
            builder.actorUserId(tryParseUuid(principal));
            builder.actorRole(auth.getAuthorities().stream()
                    .findFirst().map(a -> a.getAuthority().replace("ROLE_", "")).orElse(null));
            resolveActorEmail(principal).ifPresent(builder::actorEmail);
        }

        HttpServletRequest request = currentRequest();
        if (request != null) {
            builder.ipAddress(clientIp(request));
            builder.userAgent(truncate(request.getHeader("User-Agent"), 512));
        }
        return builder;
    }

    private Optional<String> resolveActorEmail(String principal) {
        UUID id = tryParseUuid(principal);
        if (id == null) {
            return Optional.empty();
        }
        try {
            return userRepository.findById(id).map(u -> u.getEmail());
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static UUID tryParseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (Exception e) {
            return null;
        }
    }

    private static HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return attrs.getRequest();
        }
        return null;
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return truncate(forwarded.split(",")[0].trim(), 64);
        }
        return truncate(request.getRemoteAddr(), 64);
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}

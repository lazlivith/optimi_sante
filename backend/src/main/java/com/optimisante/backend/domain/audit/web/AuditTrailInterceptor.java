package com.optimisante.backend.domain.audit.web;

import com.optimisante.backend.domain.audit.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;

/**
 * Journalise automatiquement toute requête HTTP mutante (POST / PUT / PATCH / DELETE) qui a
 * abouti, sur les périmètres sensibles (back-office admin, espace partenaire, actions RGPD).
 * Couvre d'un seul tenant l'essentiel des actions traçables sans avoir à instrumenter chaque
 * service. Les actions particulièrement sensibles ajoutent en plus un enregistrement métier
 * explicite (cf. {@code AuditService.record}).
 *
 * <p>Exécuté en {@code afterCompletion}, donc pendant que le contexte de sécurité et le
 * {@code TenantContext} de la requête sont encore actifs (le filtre JWT ne les nettoie qu'au
 * retour de la chaîne de filtres, après le {@code DispatcherServlet}).
 */
@Component
@RequiredArgsConstructor
public class AuditTrailInterceptor implements HandlerInterceptor {

    private static final Set<String> MUTATING = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final AuditService auditService;

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, @Nullable Exception ex) {
        if (!MUTATING.contains(request.getMethod().toUpperCase())) {
            return;
        }
        String path = request.getRequestURI();
        if (!isAuditablePath(path)) {
            return;
        }
        // Ne journalise pas les échecs d'authentification / validation (bruit sans valeur d'audit).
        int status = response.getStatus();
        if (status == 401 || status == 403) {
            return;
        }
        auditService.recordHttpWrite(request.getMethod().toUpperCase(), path, status);
    }

    private static boolean isAuditablePath(String path) {
        if (path == null) {
            return false;
        }
        return path.startsWith("/api/v1/admin/")
                || path.startsWith("/api/v1/partner/")
                || path.contains("/rgpd/")
                || path.startsWith("/api/v1/enrollments/");
    }
}

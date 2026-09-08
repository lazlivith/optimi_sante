package com.optimisante.backend.config;

import com.optimisante.backend.domain.audit.web.AuditTrailInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Enregistre l'intercepteur du journal d'audit sur l'ensemble des routes API. Le filtrage fin
 * (méthodes mutantes, périmètres sensibles) est fait dans l'intercepteur lui-même.
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final AuditTrailInterceptor auditTrailInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(auditTrailInterceptor).addPathPatterns("/api/**");
    }
}

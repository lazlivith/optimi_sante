package com.optimisante.backend.config.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Origines autorisées à appeler l'API depuis un navigateur.
 *
 * <p><b>Deux architectures, une seule configuration.</b> Servir le site et l'API sous le même nom
 * de domaine — un relais {@code /api} devant le backend — reste le montage le plus sûr : aucune
 * origine tierce n'est admise, par construction, et le navigateur n'envoie aucune requête
 * préalable. C'est le comportement par défaut ici : liste vide, aucun en-tête CORS émis, ce que
 * faisait déjà la plateforme.</p>
 *
 * <p>Mais héberger l'API ailleurs que le site — le backend sur un service de conteneurs, les
 * pages chez un autre hébergeur — impose de nommer les origines permises. Sans elles, le
 * navigateur bloque <b>tout</b>, et l'application paraît en panne alors que le serveur répond
 * correctement. D'où cette liste, fournie par {@code CORS_ALLOWED_ORIGINS}.</p>
 *
 * <p><b>Pas de cookie, donc pas d'identifiants à transporter.</b> Le jeton voyage dans l'en-tête
 * {@code Authorization} ; {@code allowCredentials} reste donc à faux. C'est aussi ce qui rend
 * inutile la protection CSRF, désactivée dans {@link SecurityConfig}.</p>
 */
@Slf4j
@Configuration
public class CorsConfig {

    /** Le tenant voyage dans un en-tête propre à la plateforme : sans lui, tout appel échoue. */
    private static final List<String> EN_TETES_ADMIS =
            List.of("Authorization", "Content-Type", "Accept", "X-Tenant-Id");

    /** Le nom du fichier d'un document téléchargé se lit dans cet en-tête. */
    private static final List<String> EN_TETES_EXPOSES = List.of("Content-Disposition");

    private static final List<String> METHODES =
            List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");

    @Value("${app.security.cors.allowed-origins:}")
    private String originesAutorisees;

    /**
     * Nom du bean imposé : c'est celui que {@code HttpSecurity.cors()} recherche.
     *
     * @return une source qui ne renvoie aucune règle tant qu'aucune origine n'est déclarée —
     *         aucun en-tête CORS n'est alors ajouté, exactement comme avant
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        List<String> origines = Arrays.stream(originesAutorisees.split(","))
                .map(String::trim)
                .filter(o -> !o.isEmpty())
                .toList();

        if (origines.isEmpty()) {
            log.info("CORS : aucune origine déclarée. L'API n'est appelable que depuis sa propre "
                    + "origine — montage recommandé, avec un relais /api devant le backend.");
            return request -> null;
        }

        log.info("CORS : origines autorisées — {}", String.join(", ", origines));
        CorsConfiguration regles = new CorsConfiguration();
        regles.setAllowedOrigins(origines);
        regles.setAllowedMethods(METHODES);
        regles.setAllowedHeaders(EN_TETES_ADMIS);
        regles.setExposedHeaders(EN_TETES_EXPOSES);
        regles.setAllowCredentials(false);
        // Une heure : sans cela, le navigateur redemande l'autorisation avant chaque appel
        // qui porte un en-tête, c'est-à-dire avant chacun de ceux que fait l'application.
        regles.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", regles);
        return source;
    }
}

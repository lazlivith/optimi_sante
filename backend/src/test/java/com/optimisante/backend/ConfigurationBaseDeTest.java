package com.optimisante.backend;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base de données des tests : un PostgreSQL éphémère, démarré par Testcontainers.
 *
 * <p><b>Pourquoi un vrai PostgreSQL.</b> Les migrations créent les extensions {@code pgcrypto} et
 * {@code pg_trgm}, des index GIN en {@code gin_trgm_ops}, et comparent par expression régulière.
 * Une base en mémoire ignorerait tout cela : le contexte démarrerait sans que le schéma ait été
 * réellement éprouvé, et un défaut de migration n'apparaîtrait qu'en production.</p>
 *
 * <p><b>Pourquoi un conteneur plutôt que la base locale.</b> La suite ne doit dépendre d'aucune
 * variable d'environnement ni d'un serveur déjà lancé : {@code ./mvnw test} doit passer sur un
 * poste vierge comme dans une chaîne d'intégration. {@code @ServiceConnection} fournit l'URL,
 * l'utilisateur et le mot de passe du conteneur ; rien n'est écrit dans un fichier.</p>
 *
 * <p>L'image est celle de {@code docker-compose.yml} : tester sur une version majeure différente
 * de celle qui sert en production reviendrait à tester autre chose.</p>
 */
@TestConfiguration(proxyBeanMethods = false)
public class ConfigurationBaseDeTest {

    private static final DockerImageName IMAGE = DockerImageName.parse("postgres:16-alpine");

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgres() {
        return new PostgreSQLContainer<>(IMAGE)
                .withDatabaseName("optimisante_test")
                .withReuse(false);
    }
}

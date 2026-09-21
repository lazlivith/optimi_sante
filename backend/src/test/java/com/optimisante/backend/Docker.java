package com.optimisante.backend;

import org.testcontainers.DockerClientFactory;

/**
 * Docker est-il joignable depuis cette JVM ?
 *
 * <p><b>Pourquoi cette question est posée.</b> Les tests qui démarrent le contexte applicatif ont
 * besoin d'un vrai PostgreSQL, donc d'un conteneur. Une chaîne d'intégration en fournit un ; un
 * poste de développement pas toujours. Faire échouer la build parce que Docker est absent
 * reviendrait à remplacer une dépendance à une variable d'environnement par une autre — c'est
 * précisément ce qu'on cherchait à supprimer.</p>
 *
 * <p>Ces tests sont donc <b>ignorés</b> faute de Docker, jamais en échec. La chaîne d'intégration,
 * elle, doit disposer de Docker : c'est là qu'ils font foi.</p>
 */
public final class Docker {

    private Docker() {
    }

    /** Appelé par {@code @EnabledIf} ; ne lève jamais, une panne du démon valant indisponibilité. */
    public static boolean disponible() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable e) {
            return false;
        }
    }
}

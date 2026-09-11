package com.optimisante.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Active la planification ({@code @Scheduled}) et l'exécution asynchrone ({@code @Async}).
 * L'async sert notamment au {@code NotificationDispatcher} : les notifications sont produites
 * après commit, hors du chemin critique de la requête.
 */
@Configuration
@EnableScheduling
@EnableAsync
public class SchedulingConfig {
}

package com.optimisante.backend.common.storage.cloudinary;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Réglages Cloudinary ({@code app.cloudinary.*}).
 *
 * @param rootFolder  racine de tous les fichiers d'Optimi Santé. Le compte Cloudinary est partagé
 *                    avec d'autres projets : sans racine, nos dossiers se mêlaient aux leurs.
 * @param environment sous-dossier d'environnement ({@code dev}, {@code prod}…). Les fichiers de
 *                    test ne se mêlent jamais à ceux de production, et la purge avant la mise en
 *                    production se fait en supprimant un seul dossier.
 */
@ConfigurationProperties(prefix = "app.cloudinary")
public record CloudinaryProperties(
        String cloudName,
        String apiKey,
        String apiSecret,
        String rootFolder,
        String environment) {
}

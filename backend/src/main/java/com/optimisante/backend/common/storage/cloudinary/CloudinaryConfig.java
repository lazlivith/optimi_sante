package com.optimisante.backend.common.storage.cloudinary;

import com.cloudinary.Cloudinary;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/** Client Cloudinary et arborescence, construits une fois à partir de {@link CloudinaryProperties}. */
@Configuration
public class CloudinaryConfig {

    @Bean
    public Cloudinary cloudinary(CloudinaryProperties proprietes) {
        return new Cloudinary(Map.of(
                "cloud_name", proprietes.cloudName(),
                "api_key", proprietes.apiKey(),
                "api_secret", proprietes.apiSecret()));
    }

    /** Vérifiée au démarrage : une racine invalide empêche l'application de partir. */
    @Bean
    ArborescenceCloudinary arborescenceCloudinary(CloudinaryProperties proprietes) {
        return new ArborescenceCloudinary(proprietes.rootFolder(), proprietes.environment());
    }
}

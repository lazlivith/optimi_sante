package com.optimisante.backend.domain.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/**
 * Charges utiles de la gestion media d'un produit : vignette, galerie, video.
 *
 * <p>Regroupees dans un seul fichier, comme les autres familles de DTO du projet : elles
 * decrivent un meme ecran et se lisent mieux d'un bloc.</p>
 */
public final class ProductMediaDtos {

    private ProductMediaDtos() {}

    /** Video hebergee ailleurs : YouTube, Vimeo, Loom. */
    @Data
    public static class VideoLinkRequest {
        @NotBlank(message = "L'adresse de la vidéo est obligatoire.")
        @Size(max = 1000)
        private String videoUrl;

        /** CLOUDINARY, YOUTUBE, VIMEO ou LOOM. Verifie cote service. */
        @NotBlank(message = "Précisez l'hébergeur de la vidéo.")
        @Size(max = 20)
        private String videoProvider;
    }

    /** Nouvel ordre d'affichage de la galerie, du premier au dernier. */
    @Data
    public static class ReorderGalleryRequest {
        private List<UUID> imageIds;
    }

    @Data
    @Builder
    public static class GalleryImageView {
        private UUID id;
        private String imageUrl;
        private String caption;
        private Integer displayOrder;
    }

    /** Etat media complet d'un produit, tel que l'ecran d'administration le relit. */
    @Data
    @Builder
    public static class ProductMediaView {
        private UUID productId;
        /** Vignette du catalogue : une seule image, celle qui represente le produit. */
        private String imageUrl;
        private String videoUrl;
        private String videoProvider;
        /** Vrai si la video doit etre jouee sur les cartes de promotion. */
        private boolean videoPromoted;
        private List<GalleryImageView> gallery;
    }
}

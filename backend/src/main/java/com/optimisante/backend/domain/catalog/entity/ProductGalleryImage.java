package com.optimisante.backend.domain.catalog.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Visuel secondaire d'un produit, dans une galerie ordonnee.
 *
 * <p>L'image principale reste {@code products.image_url} : c'est la vignette du catalogue, ou
 * une seule image doit etre choisie. La galerie sert la fiche detaillee — angles, zooms,
 * schemas techniques — la ou le client cherche a se faire une idee precise du materiel.</p>
 *
 * <p>Entite volontairement independante de {@code Product} du cote Java : le catalogue liste
 * 1 500 produits, et rattacher une collection a l'entite ferait charger les galeries a chaque
 * parcours de liste. Les visuels sont donc lus explicitement, quand la fiche s'ouvre.</p>
 */
@Entity
@Table(name = "product_gallery_images")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductGalleryImage {

    @Id
    @GeneratedValue(generator = "uuid2")
    @UuidGenerator
    @Column(updatable = false, nullable = false)
    private UUID id;

    /**
     * Identifiant du produit, en brut plutot qu'en association.
     *
     * <p>Une association {@code @ManyToOne} vers {@code Product} porterait son
     * {@code @SQLRestriction("deleted_at IS NULL AND is_active = true")} : l'administration ne
     * pourrait plus lire la galerie d'un produit desactive, c'est-a-dire precisement celui
     * qu'elle est en train de corriger.</p>
     */
    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "image_url", nullable = false, length = 1000)
    private String imageUrl;

    /**
     * Identifiant Cloudinary du fichier.
     *
     * <p>Conserve pour pouvoir supprimer le fichier a la source quand un visuel est retire.
     * Sans lui, la ligne disparaitrait de la galerie mais le fichier resterait dans l'espace
     * de stockage : facture, et invisible depuis l'application.</p>
     */
    @Column(name = "public_id", length = 255)
    private String publicId;

    @Column(name = "display_order", nullable = false)
    @Builder.Default
    private Integer displayOrder = 0;

    @Column(length = 255)
    private String caption;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}

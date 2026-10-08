package com.optimisante.backend.domain.catalog.repository;

import com.optimisante.backend.domain.catalog.entity.ProductGalleryImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProductGalleryImageRepository extends JpaRepository<ProductGalleryImage, UUID> {

    /** Visuels d'un produit, dans l'ordre voulu par l'administration. */
    List<ProductGalleryImage> findByProductIdOrderByDisplayOrderAscCreatedAtAsc(UUID productId);

    /**
     * Visuels de plusieurs produits en une seule requete.
     *
     * <p>Necessaire des qu'une page affiche plusieurs fiches : interroger produit par produit
     * declencherait une requete par ligne — le defaut mesure et corrige a plusieurs reprises
     * sur ce projet.</p>
     */
    List<ProductGalleryImage> findByProductIdInOrderByDisplayOrderAsc(java.util.Collection<UUID> productIds);

    /** Rang le plus eleve deja utilise : sert a ajouter un visuel a la suite des autres. */
    @org.springframework.data.jpa.repository.Query(
            "SELECT COALESCE(MAX(g.displayOrder), -1) FROM ProductGalleryImage g WHERE g.productId = :productId")
    int maxDisplayOrder(@org.springframework.data.repository.query.Param("productId") UUID productId);

    /**
     * Parmi ces produits, ceux qui ont deja au moins un visuel de galerie.
     *
     * <p>Une seule requete pour tout un lot d'import : interroger produit par produit
     * ajouterait cinquante allers-retours par lot, pour une information qui tient en une
     * liste d'identifiants.</p>
     */
    @org.springframework.data.jpa.repository.Query(
            "SELECT DISTINCT g.productId FROM ProductGalleryImage g WHERE g.productId IN :productIds")
    List<UUID> produitsAvecGalerie(
            @org.springframework.data.repository.query.Param("productIds") java.util.Collection<UUID> productIds);

    long countByProductId(UUID productId);
}

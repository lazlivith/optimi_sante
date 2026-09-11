package com.optimisante.backend.domain.catalog.controller;

import com.optimisante.backend.config.security.EcommerceAdmin;
import com.optimisante.backend.domain.catalog.dto.ProductMediaDtos.*;
import com.optimisante.backend.domain.catalog.service.ProductMediaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Medias d'un produit : vignette, galerie de details, video de demonstration.
 *
 * <p>Sous {@code /api/v1/admin/catalog}, comme le reste de l'administration du negoce — et non
 * sous {@code /api/admin/...} : le filtre de securite est branche sur le prefixe {@code /api/v1},
 * une route posee ailleurs echapperait aux gardes sans que rien ne le signale.</p>
 *
 * <p>Garde {@link EcommerceAdmin} : le catalogue releve du negoce. L'administration de la
 * mobilite n'y a pas acces, conformement a l'etancheite des deux univers.</p>
 */
@RestController
@RequestMapping("/api/v1/admin/catalog/products")
@RequiredArgsConstructor
@EcommerceAdmin
public class AdminProductMediaResource {

    private final ProductMediaService mediaService;

    /** Etat media complet : vignette, video, galerie. */
    @GetMapping("/{id}/media")
    public ResponseEntity<ProductMediaView> getMedia(@PathVariable UUID id) {
        return ResponseEntity.ok(mediaService.getMedia(id));
    }

    /**
     * Remplace la vignette du produit.
     *
     * <p>C'est l'operation du traitement en lot des produits sans visuel propre : un
     * glisser-deposer par produit, et la photo generique disparait.</p>
     */
    @PutMapping(value = "/{id}/media", consumes = "multipart/form-data")
    public ResponseEntity<ProductMediaView> replaceMainImage(@PathVariable UUID id,
                                                             @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(mediaService.replaceMainImage(id, file));
    }

    // ------------------------------------------------------------------------- video ----

    /** Televersement direct d'un fichier video (MP4, WebM, MOV) vers notre stockage. */
    @PostMapping(value = "/{id}/video", consumes = "multipart/form-data")
    public ResponseEntity<ProductMediaView> uploadVideo(@PathVariable UUID id,
                                                        @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(mediaService.setVideo(id, file, null));
    }

    /** Video hebergee ailleurs : YouTube, Vimeo, Loom. */
    @PutMapping("/{id}/video-link")
    public ResponseEntity<ProductMediaView> setVideoLink(@PathVariable UUID id,
                                                         @Valid @RequestBody VideoLinkRequest body) {
        return ResponseEntity.ok(mediaService.setVideo(id, null, body));
    }

    @DeleteMapping("/{id}/video")
    public ResponseEntity<ProductMediaView> removeVideo(@PathVariable UUID id) {
        return ResponseEntity.ok(mediaService.removeVideo(id));
    }

    /** Joue la video en boucle sur les cartes de promotion et la banniere d'accueil. */
    @PatchMapping("/{id}/video-promotion")
    public ResponseEntity<ProductMediaView> setVideoPromotion(@PathVariable UUID id,
                                                              @RequestBody Map<String, Boolean> body) {
        return ResponseEntity.ok(
                mediaService.setVideoPromoted(id, Boolean.TRUE.equals(body.get("promoted"))));
    }

    // ----------------------------------------------------------------------- galerie ----

    @GetMapping("/{id}/gallery")
    public ResponseEntity<List<GalleryImageView>> listGallery(@PathVariable UUID id) {
        return ResponseEntity.ok(mediaService.listGallery(id));
    }

    /** Ajoute un ou plusieurs visuels, a la suite de ceux deja presents. */
    @PostMapping(value = "/{id}/gallery", consumes = "multipart/form-data")
    public ResponseEntity<List<GalleryImageView>> addToGallery(
            @PathVariable UUID id,
            @RequestParam("files") List<MultipartFile> files) {
        return ResponseEntity.ok(mediaService.addToGallery(id, files));
    }

    /** Nouvel ordre d'affichage, du premier au dernier. */
    @PutMapping("/{id}/gallery/order")
    public ResponseEntity<List<GalleryImageView>> reorderGallery(
            @PathVariable UUID id,
            @RequestBody ReorderGalleryRequest body) {
        return ResponseEntity.ok(mediaService.reorderGallery(id, body.getImageIds()));
    }

    /**
     * Retire un visuel de la galerie et supprime le fichier du stockage.
     *
     * <p>Route posee sur l'identifiant du visuel et non sous celui du produit : un visuel
     * appartient deja a un produit, et repeter les deux ouvrirait la porte a une incoherence
     * entre l'un et l'autre.</p>
     */
    @DeleteMapping("/gallery/{imageId}")
    public ResponseEntity<Void> removeFromGallery(@PathVariable UUID imageId) {
        mediaService.removeFromGallery(imageId);
        return ResponseEntity.noContent().build();
    }
}

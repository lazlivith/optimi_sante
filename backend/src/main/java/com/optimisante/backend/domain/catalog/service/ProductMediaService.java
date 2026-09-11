package com.optimisante.backend.domain.catalog.service;

import com.optimisante.backend.common.storage.StorageService;
import com.optimisante.backend.domain.catalog.dto.ProductMediaDtos.*;
import com.optimisante.backend.domain.catalog.entity.ProductGalleryImage;
import com.optimisante.backend.domain.catalog.repository.AdminProductRow;
import com.optimisante.backend.domain.catalog.repository.ProductGalleryImageRepository;
import com.optimisante.backend.domain.catalog.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

/**
 * Autonomie media du catalogue : l'equipe televerse elle-meme visuels et videos.
 *
 * <p>C'est le service qui coupe le dernier lien avec l'ancien site. Tant que les images ne
 * pouvaient etre reprises que par extraction, chaque correction supposait d'interroger un
 * serveur tiers. Ici tout part du poste de l'administrateur vers notre propre stockage.</p>
 *
 * <p><b>Les ecritures passent par des requetes natives</b>, comme {@code setActiveForAdmin}.
 * {@code Product} porte un {@code @SQLRestriction("deleted_at IS NULL AND is_active = true")} :
 * un produit desactive est invisible a l'entite. Or c'est souvent celui-la qu'on corrige — on
 * desactive un produit <i>parce que</i> sa fiche est incomplete.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProductMediaService {

    /**
     * Taille maximale d'une video, en octets.
     *
     * <p>50 Mo couvrent largement une demonstration de 30 a 60 secondes en 1080p compressee.
     * Au-dela, le cout de stockage et de bande passante grimpe sans benefice pour le client,
     * qui ne regardera pas plusieurs minutes de video sur une fiche produit.</p>
     *
     * <p>⚠️ Cette limite est doublee cote servlet par {@code spring.servlet.multipart.max-file-size},
     * qui doit rester <b>superieure</b>. Si le servlet refusait le premier, l'administrateur
     * recevrait une erreur technique au lieu du message clair produit ici.</p>
     */
    public static final long TAILLE_MAX_VIDEO = 50L * 1024 * 1024;

    /** Une image de fiche produit n'a aucune raison d'etre plus lourde. */
    public static final long TAILLE_MAX_IMAGE = 10L * 1024 * 1024;

    /** Au-dela, la galerie cesse d'aider a choisir et devient un defilement penible. */
    public static final int VISUELS_MAX = 12;

    private static final Set<String> HEBERGEURS =
            Set.of("CLOUDINARY", "YOUTUBE", "VIMEO", "LOOM");

    private static final Set<String> TYPES_IMAGE =
            Set.of("image/jpeg", "image/png", "image/webp", "image/avif");

    private static final Set<String> TYPES_VIDEO =
            Set.of("video/mp4", "video/webm", "video/quicktime");

    private final ProductRepository productRepository;
    private final ProductGalleryImageRepository galleryRepository;
    private final StorageService storageService;

    // ------------------------------------------------------------------ image principale --

    /**
     * Remplace la vignette du produit.
     *
     * <p>C'est l'operation qui traite les produits sans visuel propre : un glisser-deposer, et
     * la photo generique disparait.</p>
     */
    @Transactional
    public ProductMediaView replaceMainImage(UUID productId, MultipartFile fichier) {
        AdminProductRow produit = requireProduct(productId);
        verifierImage(fichier);

        String publicId = storageService.uploadMedia(fichier, "catalog/products", "image");
        productRepository.setMainImageForAdmin(
                productId, storageService.generateMediaUrl(publicId, "image"));

        log.info("Vignette remplacée pour le produit {} ({})", productId, produit.getSku());
        return view(productId);
    }

    // --------------------------------------------------------------------------- video --

    /**
     * Definit la video du produit, par televersement ou par lien d'integration.
     *
     * <p>Les deux voies sont exclusives : envoyer un fichier <i>et</i> un lien laisserait la
     * plateforme choisir a la place de l'administrateur, et le fichier televerse serait
     * facture sans jamais etre lu.</p>
     */
    @Transactional
    public ProductMediaView setVideo(UUID productId, MultipartFile fichier, VideoLinkRequest lien) {
        requireProduct(productId);
        boolean aFichier = fichier != null && !fichier.isEmpty();
        boolean aLien = lien != null && lien.getVideoUrl() != null && !lien.getVideoUrl().isBlank();

        if (aFichier && aLien) {
            throw new IllegalArgumentException(
                    "Choisissez soit un fichier vidéo, soit un lien d'intégration — pas les deux.");
        }
        if (!aFichier && !aLien) {
            throw new IllegalArgumentException("Aucune vidéo fournie.");
        }

        String url;
        String hebergeur;

        if (aFichier) {
            verifierVideo(fichier);
            String publicId = storageService.uploadMedia(fichier, "catalog/videos", "video");
            url = storageService.generateMediaUrl(publicId, "video");
            hebergeur = "CLOUDINARY";
        } else {
            String demande = lien.getVideoProvider() == null
                    ? null : lien.getVideoProvider().trim().toUpperCase(Locale.ROOT);
            if (demande == null || !HEBERGEURS.contains(demande)) {
                throw new IllegalArgumentException(
                        "Hébergeur vidéo non reconnu. Valeurs acceptées : "
                        + String.join(", ", new TreeSet<>(HEBERGEURS)) + ".");
            }
            url = lien.getVideoUrl().trim();
            hebergeur = demande;
        }

        productRepository.setVideoForAdmin(productId, url, hebergeur);
        log.info("Vidéo définie pour le produit {} ({})", productId, hebergeur);
        return view(productId);
    }

    /** Retire la video et, avec elle, la mise en vedette : l'une n'a pas de sens sans l'autre. */
    @Transactional
    public ProductMediaView removeVideo(UUID productId) {
        requireProduct(productId);
        // La contrainte products_video_promotion_check refuserait une vedette sans video : la
        // requete remet donc `is_video_promoted` a false dans le meme ordre SQL, plutot que de
        // laisser la base rejeter l'enregistrement.
        productRepository.setVideoForAdmin(productId, null, null);
        log.info("Vidéo retirée du produit {}", productId);
        return view(productId);
    }

    /** Active ou desactive la lecture de la video sur les cartes de promotion. */
    @Transactional
    public ProductMediaView setVideoPromoted(UUID productId, boolean enVedette) {
        AdminProductRow produit = requireProduct(productId);
        if (enVedette && (produit.getVideoUrl() == null || produit.getVideoUrl().isBlank())) {
            throw new IllegalStateException(
                    "Ce produit n'a pas de vidéo : il ne peut pas être mis en vedette.");
        }
        productRepository.setVideoPromotedForAdmin(productId, enVedette);
        return view(productId);
    }

    // -------------------------------------------------------------------------- galerie --

    /** Etat media complet du produit, en lecture seule. */
    @Transactional(readOnly = true)
    public ProductMediaView getMedia(UUID productId) {
        return view(productId);
    }

    @Transactional(readOnly = true)
    public List<GalleryImageView> listGallery(UUID productId) {
        return galleryRepository.findByProductIdOrderByDisplayOrderAscCreatedAtAsc(productId)
                .stream().map(this::toView).toList();
    }

    /**
     * Ajoute un ou plusieurs visuels a la galerie, a la suite des existants.
     *
     * <p>Chaque fichier est traite independamment : si le troisieme est refuse, les deux
     * premiers restent acquis. Obliger a tout recommencer pour un seul fichier trop lourd
     * serait punitif sur un depot de dix visuels.</p>
     */
    @Transactional
    public List<GalleryImageView> addToGallery(UUID productId, List<MultipartFile> fichiers) {
        requireProduct(productId);
        if (fichiers == null || fichiers.isEmpty()) {
            throw new IllegalArgumentException("Aucun fichier reçu.");
        }

        long deja = galleryRepository.countByProductId(productId);
        if (deja + fichiers.size() > VISUELS_MAX) {
            throw new IllegalStateException(
                    "La galerie est limitée à " + VISUELS_MAX + " visuels. Ce produit en compte "
                    + deja + " : vous pouvez en ajouter " + (VISUELS_MAX - deja) + ".");
        }

        int rang = galleryRepository.maxDisplayOrder(productId) + 1;
        List<GalleryImageView> ajoutes = new ArrayList<>();

        for (MultipartFile fichier : fichiers) {
            if (fichier == null || fichier.isEmpty()) {
                continue;
            }
            verifierImage(fichier);
            String publicId = storageService.uploadMedia(fichier, "catalog/gallery", "image");
            ProductGalleryImage visuel = galleryRepository.save(ProductGalleryImage.builder()
                    .productId(productId)
                    .imageUrl(storageService.generateMediaUrl(publicId, "image"))
                    .publicId(publicId)
                    .displayOrder(rang++)
                    .build());
            ajoutes.add(toView(visuel));
        }

        log.info("{} visuel(s) ajouté(s) à la galerie du produit {}", ajoutes.size(), productId);
        return ajoutes;
    }

    /**
     * Retire un visuel de la galerie, <b>et le fichier du stockage</b>.
     *
     * <p>Supprimer la seule ligne laisserait le fichier orphelin : facture chaque mois, et
     * introuvable depuis l'application. L'echec de la suppression distante n'annule pas
     * l'operation — la ligne part quand meme, sans quoi un incident de stockage rendrait la
     * galerie impossible a corriger.</p>
     */
    @Transactional
    public void removeFromGallery(UUID imageId) {
        ProductGalleryImage visuel = galleryRepository.findById(imageId)
                .orElseThrow(() -> new IllegalArgumentException("Visuel introuvable : " + imageId));

        if (visuel.getPublicId() != null && !visuel.getPublicId().isBlank()) {
            try {
                storageService.deleteFile(visuel.getPublicId());
            } catch (Exception e) {
                log.error("Fichier {} non supprimé du stockage : {}", visuel.getPublicId(), e.getMessage());
            }
        }
        galleryRepository.delete(visuel);
        log.info("Visuel {} retiré de la galerie du produit {}", imageId, visuel.getProductId());
    }

    /** Reordonne la galerie : l'ordre recu fait foi, du premier au dernier. */
    @Transactional
    public List<GalleryImageView> reorderGallery(UUID productId, List<UUID> ordre) {
        List<ProductGalleryImage> visuels =
                galleryRepository.findByProductIdOrderByDisplayOrderAscCreatedAtAsc(productId);
        Map<UUID, ProductGalleryImage> parId = new HashMap<>();
        visuels.forEach(v -> parId.put(v.getId(), v));

        // Un identifiant etranger au produit signale une erreur d'appel : l'accepter
        // reordonnerait silencieusement la galerie d'un autre produit.
        for (UUID id : ordre) {
            if (!parId.containsKey(id)) {
                throw new IllegalArgumentException(
                        "Le visuel " + id + " n'appartient pas à ce produit.");
            }
        }

        int rang = 0;
        for (UUID id : ordre) {
            parId.get(id).setDisplayOrder(rang++);
        }
        galleryRepository.saveAll(visuels);
        return listGallery(productId);
    }

    // -------------------------------------------------------------------------- interne --

    private void verifierImage(MultipartFile fichier) {
        if (fichier == null || fichier.isEmpty()) {
            throw new IllegalArgumentException("Aucun fichier reçu.");
        }
        if (fichier.getSize() > TAILLE_MAX_IMAGE) {
            throw new IllegalArgumentException(
                    "L'image dépasse la limite autorisée de " + enMo(TAILLE_MAX_IMAGE)
                    + " Mo (fichier reçu : " + enMo(fichier.getSize()) + " Mo).");
        }
        String type = fichier.getContentType();
        if (type == null || !TYPES_IMAGE.contains(type.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException(
                    "Format d'image non accepté. Formats reconnus : JPEG, PNG, WebP, AVIF.");
        }
    }

    private void verifierVideo(MultipartFile fichier) {
        if (fichier.getSize() > TAILLE_MAX_VIDEO) {
            throw new IllegalArgumentException(
                    "La vidéo dépasse la limite autorisée de " + enMo(TAILLE_MAX_VIDEO)
                    + " Mo (fichier reçu : " + enMo(fichier.getSize()) + " Mo).");
        }
        String type = fichier.getContentType();
        if (type == null || !TYPES_VIDEO.contains(type.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException(
                    "Format vidéo non accepté. Formats reconnus : MP4, WebM, MOV.");
        }
    }

    /** Arrondi au dixieme : « 52,4 Mo » se comprend mieux que « 54 975 581 octets ». */
    private static String enMo(long octets) {
        return String.format(Locale.FRENCH, "%.1f", octets / (1024.0 * 1024.0));
    }

    private AdminProductRow requireProduct(UUID productId) {
        return productRepository.findByIdForAdmin(productId)
                .orElseThrow(() -> new IllegalArgumentException("Produit introuvable : " + productId));
    }

    /** Relit l'etat apres ecriture : l'ecran affiche ce que la base contient, et non ce qu'on
     *  croit y avoir mis. */
    private ProductMediaView view(UUID productId) {
        AdminProductRow p = requireProduct(productId);
        return ProductMediaView.builder()
                .productId(p.getId())
                .imageUrl(p.getImageUrl())
                .videoUrl(p.getVideoUrl())
                .videoProvider(p.getVideoProvider())
                .videoPromoted(Boolean.TRUE.equals(p.getIsVideoPromoted()))
                .gallery(listGallery(productId))
                .build();
    }

    private GalleryImageView toView(ProductGalleryImage g) {
        return GalleryImageView.builder()
                .id(g.getId())
                .imageUrl(g.getImageUrl())
                .caption(g.getCaption())
                .displayOrder(g.getDisplayOrder())
                .build();
    }
}

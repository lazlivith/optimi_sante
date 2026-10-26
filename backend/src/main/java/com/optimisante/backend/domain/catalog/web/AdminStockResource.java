package com.optimisante.backend.domain.catalog.web;

import com.optimisante.backend.config.security.EcommerceAdmin;
import com.optimisante.backend.domain.catalog.dto.StockDtos.AjustementRequest;
import com.optimisante.backend.domain.catalog.dto.StockDtos.LigneMouvement;
import com.optimisante.backend.domain.catalog.dto.StockDtos.LigneStock;
import com.optimisante.backend.domain.catalog.dto.StockDtos.ResumeStock;
import com.optimisante.backend.domain.catalog.dto.StockDtos.SeuilRequest;
import com.optimisante.backend.domain.catalog.entity.MotifMouvementStock;
import com.optimisante.backend.domain.catalog.entity.MouvementStock;
import com.optimisante.backend.domain.catalog.repository.MouvementStockRepository;
import com.optimisante.backend.domain.catalog.repository.ProductRepository;
import com.optimisante.backend.domain.catalog.service.StockQueryService;
import com.optimisante.backend.domain.catalog.service.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Écran admin « Stock ».
 *
 * <p>Périmètre d'accès : négoce ({@code ADMIN_ECOMMERCE}, {@code SUPER_ADMIN},
 * {@code ADMIN} hérité). Le stock est une affaire de catalogue et de commandes ; la mobilité
 * n'a rien à y faire.</p>
 *
 * <p>Aucun endpoint n'écrit la quantité directement : tout passe par {@code StockService}, afin
 * qu'aucune variation n'échappe au journal. C'est ce qui permet de répondre, plus tard, à
 * « d'où vient ce chiffre ».</p>
 */
@RestController
@RequestMapping("/api/v1/admin/stock")
@RequiredArgsConstructor
@EcommerceAdmin
public class AdminStockResource {

    /** Une page d'écran, pas un export : au-delà, on filtre ou on cherche. */
    private static final int LIMITE_MAX = 300;

    private final StockQueryService stockQueryService;
    private final StockService stockService;
    private final MouvementStockRepository mouvementRepository;
    private final ProductRepository productRepository;

    @GetMapping
    public List<LigneStock> lignes(@RequestParam(required = false) String q,
                                   @RequestParam(required = false) String filtre,
                                   @RequestParam(defaultValue = "100") int limite) {
        return stockQueryService.lignes(q, filtre, Math.min(Math.max(limite, 1), LIMITE_MAX));
    }

    @GetMapping("/resume")
    public ResumeStock resume() {
        return stockQueryService.resume();
    }

    /** Journal général, tous produits confondus : la vue « que s'est-il passé aujourd'hui ». */
    @GetMapping("/mouvements")
    public List<LigneMouvement> mouvements(@RequestParam(defaultValue = "50") int limite) {
        return mouvementRepository.journal(PageRequest.of(0, Math.min(Math.max(limite, 1), LIMITE_MAX)))
                .map(AdminStockResource::versLigne)
                .getContent();
    }

    /** Historique d'un produit : l'explication de son chiffre courant, mouvement par mouvement. */
    @GetMapping("/{productId}/mouvements")
    public List<LigneMouvement> historique(@PathVariable UUID productId,
                                           @RequestParam(defaultValue = "50") int limite) {
        return mouvementRepository.historique(productId, PageRequest.of(0, Math.min(Math.max(limite, 1), LIMITE_MAX)))
                .map(AdminStockResource::versLigne)
                .getContent();
    }

    /**
     * Enregistre un mouvement.
     *
     * <p>Deux formes selon le motif, et la distinction n'est pas cosmétique : un inventaire se
     * saisit en quantité constatée (« j'en compte 12 »), un réapprovisionnement ou une
     * correction en écart (« +40 », « −3 »). Demander un écart pour un comptage obligerait à
     * le calculer de tête, ce qui est le meilleur moyen de se tromper de signe.</p>
     */
    @PostMapping("/{productId}/ajustement")
    public Map<String, Object> ajuster(@PathVariable UUID productId,
                                       @RequestBody AjustementRequest requete) {
        MotifMouvementStock motif = motifValide(requete.motif());
        UUID auteur = auteurCourant();

        int stock = motif == MotifMouvementStock.INVENTAIRE
                ? stockService.inventorier(productId, exigerQuantite(requete), auteur, requete.commentaire())
                : stockService.appliquer(productId, exigerDelta(requete), motif, null, auteur, requete.commentaire());

        return Map.of("productId", productId, "stock", stock);
    }

    /** Le seuil en deçà duquel ce produit est signalé. Zéro désactive la surveillance. */
    @PutMapping("/{productId}/seuil")
    public ResponseEntity<Map<String, Object>> seuil(@PathVariable UUID productId,
                                                     @RequestBody SeuilRequest requete) {
        if (requete == null || requete.seuil() == null || requete.seuil() < 0) {
            throw new IllegalArgumentException("Le seuil doit être un entier positif ou nul.");
        }
        var produit = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Produit introuvable : " + productId));
        produit.setStockThreshold(requete.seuil());
        productRepository.save(produit);
        return ResponseEntity.ok(Map.of("productId", productId, "seuil", requete.seuil()));
    }

    // ------------------------------------------------------------------ interne

    private static MotifMouvementStock motifValide(String motif) {
        if (motif == null || motif.isBlank()) {
            throw new IllegalArgumentException("Le motif du mouvement est obligatoire.");
        }
        try {
            MotifMouvementStock valeur = MotifMouvementStock.valueOf(motif.trim().toUpperCase());
            if (valeur == MotifMouvementStock.VENTE) {
                // Une vente s'enregistre au paiement, jamais à la main : l'autoriser ici
                // permettrait de fabriquer un historique de ventes sans commande.
                throw new IllegalArgumentException(
                        "Une sortie pour vente ne se saisit pas : elle découle d'une commande payée.");
            }
            return valeur;
        } catch (IllegalArgumentException e) {
            if (e.getMessage() != null && e.getMessage().startsWith("Une sortie")) {
                throw e;
            }
            throw new IllegalArgumentException("Motif inconnu : " + motif);
        }
    }

    private static int exigerDelta(AjustementRequest requete) {
        if (requete.delta() == null || requete.delta() == 0) {
            throw new IllegalArgumentException("Indiquez une variation non nulle (ex. 40 ou -3).");
        }
        return requete.delta();
    }

    private static int exigerQuantite(AjustementRequest requete) {
        if (requete.quantiteConstatee() == null || requete.quantiteConstatee() < 0) {
            throw new IllegalArgumentException("Indiquez la quantité comptée (zéro ou plus).");
        }
        return requete.quantiteConstatee();
    }

    private static UUID auteurCourant() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }
        try {
            return UUID.fromString(auth.getPrincipal().toString());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static LigneMouvement versLigne(MouvementStock m) {
        return new LigneMouvement(
                m.getId(),
                m.getProduct() == null ? null : m.getProduct().getId(),
                m.getProduct() == null ? null : m.getProduct().getName(),
                m.getDelta(),
                m.getStockApres(),
                m.getMotif() == null ? null : m.getMotif().name(),
                m.getOrderId(),
                m.getAuteur() == null ? null : m.getAuteur().getEmail(),
                m.getCommentaire(),
                m.getCreatedAt());
    }
}

package com.optimisante.backend.domain.catalog.entity;

import com.optimisante.backend.domain.identity.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Une variation de stock, et sa raison.
 *
 * <p>Avant ce journal, {@code products.stock_quantity} était décrémenté au paiement sans
 * laisser de trace : quand un chiffre devenait faux, rien ne permettait de dire ni quand ni
 * pourquoi. Chaque ligne porte le delta <b>et</b> le stock obtenu, de sorte qu'un historique
 * se relit sans rejouer les calculs, et qu'un écart saute aux yeux.</p>
 *
 * <p>Ces lignes ne sont jamais modifiées ni supprimées : corriger une erreur de stock s'écrit
 * en ajoutant un mouvement, pas en réécrivant le passé.</p>
 */
@Entity
@Table(name = "stock_mouvements")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MouvementStock {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /** Négatif pour une sortie, positif pour une entrée. Jamais nul. */
    @Column(nullable = false)
    private Integer delta;

    /** Stock du produit une fois le mouvement appliqué. */
    @Column(name = "stock_apres", nullable = false)
    private Integer stockApres;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private MotifMouvementStock motif;

    /** La commande à l'origine d'une vente ou d'une annulation. Nul pour un geste manuel. */
    @Column(name = "order_id")
    private UUID orderId;

    /**
     * De quel import vient ce mouvement, quand il en vient un.
     *
     * <p>Un import de catalogue en ecrit des milliers d'un coup : sans cette reference, ils
     * seraient indiscernables d'un geste humain ou d'une vente, et impossibles a retrouver
     * ensemble si l'import s'est mal passe.</p>
     */
    @Column(name = "import_id")
    private UUID importId;

    /** Qui a agi, quand c'est un humain. Nul pour un mouvement déclenché par le système. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "auteur_id")
    private User auteur;

    @Column(length = 500)
    private String commentaire;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}

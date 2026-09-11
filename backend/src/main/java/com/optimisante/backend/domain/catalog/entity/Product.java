package com.optimisante.backend.domain.catalog.entity;

import com.optimisante.backend.domain.identity.entity.Tenant;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "products")
@SQLRestriction("deleted_at IS NULL AND is_active = true")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(nullable = false, unique = true, length = 100)
    private String sku;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false, length = 255)
    private String slug;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "base_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal basePrice;

    @Column(name = "stock_quantity")
    @Builder.Default
    private Integer stockQuantity = 0;

    @Column(name = "stock_threshold")
    @Builder.Default
    private Integer stockThreshold = 5;

    @Column(name = "is_quote_only")
    @Builder.Default
    private Boolean isQuoteOnly = false;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Column(name = "image_url", length = 512)
    private String imageUrl;

    @Column(name = "promo_price", precision = 10, scale = 2)
    private BigDecimal promoPrice;

    /**
     * Formation qui apprend a utiliser cet equipement. Relation 1:1, garantie par l'index
     * unique {@code uq_products_training} (V38) — la seule annotation JPA ne l'imposerait pas.
     *
     * <p>{@code ManyToOne} plutot que {@code OneToOne} : Hibernate charge un {@code OneToOne}
     * facultatif de facon anticipee meme declare paresseux, ce qui ferait une requete par
     * produit sur les listes de catalogue. L'unicite est portee par la base, pas par le
     * mapping.</p>
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "training_id")
    private com.optimisante.backend.domain.training.entity.Training training;

    @Column(name = "promo_starts_at")
    private OffsetDateTime promoStartsAt;

    @Column(name = "promo_ends_at")
    private OffsetDateTime promoEndsAt;

    /**
     * Video de demonstration du produit : televersee sur Cloudinary, ou lien d'integration.
     *
     * <p>Trois regles sont imposees par la base (V53) et non par l'ecran : une video exige son
     * hebergeur, un hebergeur exige sa video, et la mise en vedette exige qu'une video existe.
     * Sans elles, une carte produit pourrait s'animer en page d'accueil sur un cadre vide.</p>
     */
    @Column(name = "video_url", length = 1000)
    private String videoUrl;

    /** {@code CLOUDINARY}, {@code YOUTUBE}, {@code VIMEO} ou {@code LOOM}. Dit au lecteur s'il
     *  doit poser une balise video ou une iframe. */
    @Column(name = "video_provider", length = 20)
    private String videoProvider;

    /** Joue la video en boucle sur la carte produit des sections Promotion et Hero. */
    @Column(name = "is_video_promoted", nullable = false)
    @Builder.Default
    private Boolean isVideoPromoted = false;

    @Version
    @Builder.Default
    private Integer version = 1;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = OffsetDateTime.now();
    }

    /**
     * Prix de base à utiliser (avant remise B2B) : le prix promotionnel s'il est défini et que
     * la date courante tombe dans la fenêtre [promoStartsAt, promoEndsAt], sinon le prix normal.
     * Point unique de vérité utilisé à la fois par l'affichage catalogue et le calcul du
     * montant réellement facturé au checkout, pour qu'ils ne divergent jamais.
     */
    @Transient
    public BigDecimal getEffectiveBasePrice() {
        if (promoPrice == null) {
            return basePrice;
        }
        OffsetDateTime now = OffsetDateTime.now();
        boolean startOk = promoStartsAt == null || !now.isBefore(promoStartsAt);
        boolean endOk = promoEndsAt == null || !now.isAfter(promoEndsAt);
        return (startOk && endOk) ? promoPrice : basePrice;
    }

    @Transient
    public boolean isPromoActive() {
        return promoPrice != null && getEffectiveBasePrice().compareTo(basePrice) < 0;
    }
}

package com.optimisante.backend.domain.orders.entity;

import com.optimisante.backend.domain.identity.entity.Tenant;
import com.optimisante.backend.domain.identity.entity.User;
import com.optimisante.backend.domain.promotion.entity.PromoCode;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(name = "order_number", nullable = false, unique = true, length = 50)
    private String orderNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", length = 50)
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", length = 30)
    @Builder.Default
    private PaymentStatus paymentStatus = PaymentStatus.UNPAID;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    @Builder.Default
    private OrderStatus status = OrderStatus.PENDING;

    @Column(name = "is_quote")
    @Builder.Default
    private Boolean isQuote = false;

    @Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    /**
     * Adresse de destination, RECOPIEE sur la commande.
     *
     * <p>Une commande est un engagement date : elle garde l'adresse telle qu'elle etait au
     * moment de l'achat. La lire sur le compte client ferait reecrire la destination des
     * anciennes commandes au premier demenagement — alors que le transporteur, lui, a livre
     * a l'ancienne.</p>
     */
    @Column(name = "shipping_recipient", length = 150)
    private String shippingRecipient;

    @Column(name = "shipping_line1", length = 255)
    private String shippingLine1;

    @Column(name = "shipping_line2", length = 255)
    private String shippingLine2;

    @Column(name = "shipping_postal_code", length = 20)
    private String shippingPostalCode;

    @Column(name = "shipping_city", length = 120)
    private String shippingCity;

    /** Code ISO 3166-1 alpha-2 du pays de destination. */
    @Column(name = "shipping_country", length = 2)
    private String shippingCountry;

    /** Zone retenue au moment de la commande. Figee : un redecoupage ne reecrit pas le passe. */
    @Enumerated(EnumType.STRING)
    @Column(name = "shipping_zone", length = 30)
    private com.optimisante.backend.domain.orders.shipping.ZoneLivraison shippingZone;

    @Column(name = "shipping_cost", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal shippingCost = BigDecimal.ZERO;

    @Column(name = "stripe_payment_intent_id", length = 255)
    private String stripePaymentIntentId;

    /**
     * Montant cumulé rendu au client sur cette commande.
     *
     * <p>Porté par la commande et non par un statut : un remboursement partiel ne fait pas
     * d'une commande une commande non honorée, mais il doit sortir sa part du chiffre
     * d'affaires. Les agrégats financiers déduisent donc cette colonne (V65).</p>
     */
    @Builder.Default
    @Column(name = "refunded_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal refundedAmount = BigDecimal.ZERO;

    @Column(name = "stripe_checkout_session_id", length = 255)
    private String stripeCheckoutSessionId;

    @Column(name = "document_s3_key", length = 255)
    private String documentS3Key;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "promo_code_id")
    private PromoCode promoCode;

    @Column(name = "discount_amount", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal discountAmount = BigDecimal.ZERO;

    /**
     * Remise accordée par l'administration sur un devis, en pourcentage (V59).
     *
     * <p>{@code discountAmount} porte déjà le montant, mais pas son origine : sans le taux et son
     * auteur, un devis remisé ne dit plus ni de combien ni par qui une fois la remise fondue dans
     * le total.</p>
     */
    @Column(name = "quote_discount_rate", precision = 5, scale = 2)
    private BigDecimal quoteDiscountRate;

    @Column(name = "quote_adjusted_by")
    private java.util.UUID quoteAdjustedBy;

    @Column(name = "quote_adjusted_at")
    private java.time.OffsetDateTime quoteAdjustedAt;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<OrderItem> items = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        this.createdAt = OffsetDateTime.now();
    }
}

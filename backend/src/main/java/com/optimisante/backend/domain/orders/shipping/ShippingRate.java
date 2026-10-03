package com.optimisante.backend.domain.orders.shipping;

import com.optimisante.backend.domain.identity.entity.Tenant;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Le tarif de livraison d'une zone.
 *
 * <p>En base et non dans le code : les tarifs d'un transporteur changent, et un changement de
 * tarif ne doit pas demander un redéploiement. L'administration les saisit.</p>
 */
@Entity
@Table(name = "shipping_rates")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShippingRate {

    @Id
    @GeneratedValue(generator = "uuid2")
    @UuidGenerator
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ZoneLivraison zone;

    @Column(nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal amount = BigDecimal.ZERO;

    /** Montant de commande à partir duquel la livraison est offerte. {@code null} : jamais. */
    @Column(name = "free_from", precision = 10, scale = 2)
    private BigDecimal freeFrom;

    /** Une zone désactivée n'est plus proposée : la commande est refusée, pas facturée à zéro. */
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}

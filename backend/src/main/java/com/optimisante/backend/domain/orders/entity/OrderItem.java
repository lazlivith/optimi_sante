package com.optimisante.backend.domain.orders.entity;

import com.optimisante.backend.domain.catalog.entity.Product;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "order_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal subtotal;

    /**
     * Taux de TVA retenu au moment de la vente, en pourcentage.
     *
     * <p>Recopie ici, et non lu sur le produit a l'affichage : un document fiscal doit
     * refleter le taux applicable le jour de la vente. Si le taux d'un produit est corrige
     * plus tard, ou si la loi change, les factures deja emises ne doivent pas se mettre a
     * jour toutes seules — elles decriraient une operation qui n'a jamais eu lieu.</p>
     */
    @Column(name = "vat_rate", nullable = false, precision = 4, scale = 2)
    @Builder.Default
    private BigDecimal vatRate = new BigDecimal("20.00");
}

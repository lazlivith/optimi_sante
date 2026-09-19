package com.optimisante.backend.domain.catalog.entity;

import com.optimisante.backend.domain.identity.entity.Tenant;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "categories", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"tenant_id", "slug"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, length = 150)
    private String slug;

    /**
     * Marge appliquée au prix d'achat des produits de cette catégorie, en pourcentage (V59).
     *
     * <p>Prioritaire sur la commission du fournisseur : une marge se décide d'abord par famille de
     * produits. Nulle, on retombe sur le fournisseur.</p>
     */
    @Column(name = "margin_rate", precision = 5, scale = 2)
    private java.math.BigDecimal marginRate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Category parent;

    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Category> subcategories = new ArrayList<>();
}

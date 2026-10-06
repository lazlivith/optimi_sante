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

    /** Taux de TVA par defaut des produits du rayon, en pourcentage. {@code null} : taux normal. */
    @Column(name = "vat_rate", precision = 4, scale = 2)
    private java.math.BigDecimal vatRate;

    /**
     * Le comptable a signalé ce taux comme à confirmer sur la liste officielle (V68).
     *
     * <p>La catégorie porte alors le taux <b>prudent</b> en attendant. Prudent dans un sens
     * précis : les prix étant annoncés TTC, extraire 20 % là où 5,5 % s'applique fait reverser
     * à l'État plus que dû — on y perd de la marge ; l'inverse est un manquement déclaratif.</p>
     */
    @Builder.Default
    @Column(name = "vat_rate_a_verifier", nullable = false)
    private Boolean vatRateAVerifier = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Category parent;

    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Category> subcategories = new ArrayList<>();
}

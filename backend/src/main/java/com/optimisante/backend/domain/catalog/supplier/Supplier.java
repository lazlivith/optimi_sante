package com.optimisante.backend.domain.catalog.supplier;

import com.optimisante.backend.domain.identity.entity.Tenant;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Fournisseur du catalogue : qui livre les produits vendus par la boutique.
 *
 * <p>À ne pas confondre avec {@code PartnerProfile}, l'établissement d'accueil des formations :
 * l'un fournit des produits et se gère depuis l'espace Négoce, l'autre accueille des médecins et
 * reçoit des reversements. Deux métiers, deux tables.</p>
 *
 * <p><b>Un produit sans fournisseur reste valable.</b> Les 1 518 références importées de
 * WooCommerce n'en ont pas, et c'est ce qui les met à l'abri : un import ne modifie que les
 * produits déjà rattachés au fournisseur qui dépose le fichier.</p>
 */
@Entity
@Table(name = "suppliers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Supplier {

    @Id
    @GeneratedValue(generator = "uuid2")
    @UuidGenerator
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    /** Référence courte manipulée par l'équipe, unique par tenant (« FOURN-001 »). */
    @Column(nullable = false, length = 40)
    private String code;

    @Column(name = "company_name", nullable = false, length = 255)
    private String companyName;

    /** SIRET, NIU ou équivalent : la forme varie selon le pays du fournisseur. */
    @Column(name = "tax_id", length = 50)
    private String taxId;

    @Column(name = "contact_name", length = 150)
    private String contactName;

    @Column(name = "contact_email", length = 255)
    private String contactEmail;

    @Column(name = "contact_phone", length = 30)
    private String contactPhone;

    /** Commission négociée, en pourcentage. Informative : aucun reversement n'y est branché. */
    @Column(name = "commission_rate", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal commissionRate = BigDecimal.ZERO;

    @Column(columnDefinition = "TEXT")
    private String notes;

    /**
     * Fournisseur inactif : on ne lui dépose plus de catalogue, mais ses produits restent en
     * vente. Désactiver n'est pas supprimer — l'historique des imports doit rester lisible.
     */
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        OffsetDateTime maintenant = OffsetDateTime.now();
        if (createdAt == null) {
            createdAt = maintenant;
        }
        updatedAt = maintenant;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}

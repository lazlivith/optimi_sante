package com.optimisante.backend.domain.identity.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Table(name = "company_profiles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CompanyProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "company_name", nullable = false, length = 255)
    private String companyName;

    /**
     * Identifiant légal/fiscal générique, à portée internationale : Tax ID, N° TVA,
     * ICE, SIRET, Registration No. selon le pays. Anciennement `siret_finess` (V23).
     */
    @Column(name = "tax_id", nullable = false, length = 50)
    private String taxId;

    @Column(name = "vat_number", length = 50)
    private String vatNumber;

    /** Pays de domiciliation de la structure (nom ou code ISO). */
    @Column(name = "country", length = 100)
    private String country;

    @Enumerated(EnumType.STRING)
    @Column(name = "facility_type", length = 50)
    private FacilityType facilityType;

    /** Nom et prénom du contact référent / acheteur au sein de la structure. */
    @Column(name = "contact_name", length = 150)
    private String contactName;

    /**
     * Adresse de facturation : plus demandée à l'inscription (collectée au devis/commande).
     * Nullable depuis V23 — les valeurs existantes sont conservées.
     */
    @Column(name = "billing_address", columnDefinition = "TEXT")
    private String billingAddress;

    @Column(name = "b2b_discount_rate", precision = 5, scale = 2)
    private BigDecimal b2bDiscountRate = BigDecimal.ZERO;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private ZonedDateTime updatedAt;
}

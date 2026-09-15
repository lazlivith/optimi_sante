package com.optimisante.backend.domain.identity.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "partner_profiles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PartnerProfile {

    @Id
    @GeneratedValue(generator = "uuid2")
    @UuidGenerator
    @Column(updatable = false, nullable = false)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "institution_name", nullable = false)
    private String institutionName;

    @Column(name = "finess_accreditation", nullable = false)
    private String finessAccreditation;

    @Column(name = "contact_person_name", nullable = false)
    private String contactPersonName;

    @Column(name = "contact_email", nullable = false)
    private String contactEmail;

    @Column(name = "contact_phone", nullable = false)
    private String contactPhone;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String address;

    /**
     * Taux de commission d'agence retenu par OptimiSanté sur les frais de formation (V27).
     * Copié sur chaque ligne de paiement à l'encaissement : le renégocier ne réécrit jamais
     * l'historique comptable déjà constaté.
     */
    @Builder.Default
    @Column(name = "commission_rate", nullable = false, precision = 5, scale = 2)
    private java.math.BigDecimal commissionRate = new java.math.BigDecimal("15.00");

    @Column(name = "is_verified")
    private Boolean isVerified;

    /**
     * Compte crédité par les reversements. Forme normalisée — sans espaces, en majuscules —
     * et clé de contrôle vérifiée avant enregistrement (voir CoordonneesBancaires). Nul tant que
     * le partenaire n'a pas transmis son RIB.
     */
    @Column(length = 34)
    private String iban;

    /** Facultatif en zone SEPA depuis 2016 ; conservé quand le partenaire le fournit. */
    @Column(length = 11)
    private String bic;

    /** Titulaire du compte tel qu'il figure sur le RIB — pas toujours le nom de l'établissement. */
    @Column(name = "bank_account_holder", length = 140)
    private String bankAccountHolder;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;
}

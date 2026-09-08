package com.optimisante.backend.domain.training.finance;

import com.optimisante.backend.domain.identity.entity.PartnerProfile;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Reversement à un partenaire : regroupe les parts nettes de plusieurs encaissements en un
 * seul virement, sur une période donnée.
 *
 * <p>Le montant est figé à la génération plutôt que recalculé à l'affichage : un reversement
 * est un engagement financier daté, il ne doit pas bouger si un paiement est remboursé
 * après coup — ce cas se traite par un reversement correctif, pas par une réécriture.</p>
 */
@Entity
@Table(name = "partner_payouts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PartnerPayout {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "partner_profile_id", nullable = false)
    private PartnerProfile partnerProfile;

    @Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @Builder.Default
    @Column(nullable = false, length = 3)
    private String currency = "EUR";

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(nullable = false, length = 20)
    private PayoutStatus status = PayoutStatus.PENDING;

    /** Référence lisible communiquée au partenaire (ex. REV-2026-09-A1B2C3). */
    @Column(length = 50, unique = true)
    private String reference;

    @Column(name = "period_start")
    private LocalDate periodStart;

    @Column(name = "period_end")
    private LocalDate periodEnd;

    /** Clé du relevé PDF déposé sur le stockage ; null tant qu'il n'a pas été généré. */
    @Column(name = "statement_s3_key", length = 255)
    private String statementS3Key;

    @Column(name = "paid_at")
    private OffsetDateTime paidAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onPersist() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }
}

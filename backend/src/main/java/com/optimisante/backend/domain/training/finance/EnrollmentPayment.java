package com.optimisante.backend.domain.training.finance;

import com.optimisante.backend.domain.training.entity.Enrollment;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Une ligne du registre financier : un encaissement rattaché à une candidature, avec sa
 * répartition figée entre commission OptimiSanté et part nette du partenaire.
 *
 * <p>Le taux est stocké sur la ligne (et non relu depuis le partenaire) : renégocier une
 * commission ne doit jamais réécrire l'historique comptable déjà constaté.</p>
 */
@Entity
@Table(name = "enrollment_payments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EnrollmentPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "enrollment_id", nullable = false)
    private Enrollment enrollment;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_type", nullable = false, length = 20)
    private PaymentType paymentType;

    /**
     * Rang dans l'echeancier, pour les seuls frais de formation.
     *
     * <p>{@code null} pour les autres types d'encaissement : les frais de dossier et les
     * services ne s'echelonnent pas. La contrainte de la V45 impose cette correspondance dans
     * les deux sens.</p>
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "installment", length = 20)
    private PaymentInstallment installment;

    @Column(name = "gross_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal grossAmount;

    @Builder.Default
    @Column(name = "commission_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal commissionRate = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "commission_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal commissionAmount = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "partner_payout_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal partnerPayoutAmount = BigDecimal.ZERO;

    @Builder.Default
    @Column(nullable = false, length = 3)
    private String currency = "EUR";

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(nullable = false, length = 20)
    private PaymentStatus status = PaymentStatus.PENDING;

    @Column(name = "stripe_checkout_session_id", length = 255)
    private String stripeCheckoutSessionId;

    @Column(name = "stripe_payment_intent_id", length = 255)
    private String stripePaymentIntentId;

    /** Reversement auquel cette ligne a été rattachée ; null tant qu'elle n'est pas reversée. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "partner_payout_id")
    private PartnerPayout partnerPayout;

    @Column(name = "paid_at")
    private OffsetDateTime paidAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onPersist() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }
}

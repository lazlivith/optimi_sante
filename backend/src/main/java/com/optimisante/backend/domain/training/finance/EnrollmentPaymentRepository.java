package com.optimisante.backend.domain.training.finance;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EnrollmentPaymentRepository extends JpaRepository<EnrollmentPayment, UUID> {

    List<EnrollmentPayment> findByEnrollmentIdOrderByCreatedAtDesc(UUID enrollmentId);

    /** Sert l'idempotence applicative : un encaissement déjà constaté n'est jamais rejoué. */
    Optional<EnrollmentPayment> findByEnrollmentIdAndPaymentTypeAndStatus(
            UUID enrollmentId, PaymentType paymentType, PaymentStatus status);

    Optional<EnrollmentPayment> findByStripeCheckoutSessionId(String stripeCheckoutSessionId);

    /**
     * Encaissements de formation d un partenaire, réglés et pas encore reversés :
     * l assiette du prochain virement.
     *
     * Volontairement sans filtre de période dans la requête : un paramètre date nullable
     * en JPQL fait échouer PostgreSQL sur l inférence de type (leçon de l entrée #48,
     * « function lower(bytea) does not exist »). Le bornage se fait côté service.
     */
    @Query("""
            SELECT p FROM EnrollmentPayment p
            WHERE p.paymentType = com.optimisante.backend.domain.training.finance.PaymentType.TUITION_FEE
              AND p.status = com.optimisante.backend.domain.training.finance.PaymentStatus.PAID
              AND p.partnerPayout IS NULL
              AND p.enrollment.session.training.partnerProfile.id = :partnerProfileId
            ORDER BY p.paidAt
            """)
    List<EnrollmentPayment> findReversiblePayments(@Param("partnerProfileId") UUID partnerProfileId);

    @Query("""
            SELECT COALESCE(SUM(p.partnerPayoutAmount), 0) FROM EnrollmentPayment p
            WHERE p.paymentType = com.optimisante.backend.domain.training.finance.PaymentType.TUITION_FEE
              AND p.status = com.optimisante.backend.domain.training.finance.PaymentStatus.PAID
              AND p.partnerPayout IS NULL
              AND p.enrollment.session.training.partnerProfile.id = :partnerProfileId
            """)
    BigDecimal sumPendingPayout(@Param("partnerProfileId") UUID partnerProfileId);

    /** Lignes rattachées à un reversement, pour le détail du relevé PDF. */
    /** Ligne d'une echeance precise : c'est le couple (dossier, rang) qui est unique (V45). */
    Optional<EnrollmentPayment> findByEnrollmentIdAndPaymentTypeAndInstallmentAndStatus(
            UUID enrollmentId, PaymentType paymentType,
            PaymentInstallment installment, PaymentStatus status);

    List<EnrollmentPayment> findByPartnerPayoutId(UUID partnerPayoutId);

    /**
     * Lignes précises à reverser, verrouillées jusqu'à la fin de la transaction.
     *
     * <p>Le reversement se déclenche désormais d'un clic, ligne par ligne : deux clics rapprochés
     * — ou deux administrateurs sur le même dossier — liraient sinon tous deux la ligne comme
     * « non reversée », et le CHU recevrait deux virements pour le même acompte. Le second attend
     * le verrou, puis constate que la ligne est déjà rattachée.</p>
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM EnrollmentPayment p WHERE p.id IN :ids")
    List<EnrollmentPayment> findAllByIdForUpdate(@Param("ids") Collection<UUID> ids);

    /**
     * Tous les encaissements des dossiers d'un partenaire — formation, options, frais de dossier,
     * réglés ou non — chargés d'un seul coup avec ce que la vue « dossiers » affiche.
     */
    @Query("""
            SELECT p FROM EnrollmentPayment p
            JOIN FETCH p.enrollment e
            JOIN FETCH e.doctor
            JOIN FETCH e.session s
            JOIN FETCH s.training t
            LEFT JOIN FETCH p.partnerPayout
            WHERE t.partnerProfile.id = :partnerProfileId
            ORDER BY p.paidAt
            """)
    List<EnrollmentPayment> findAllForPartner(@Param("partnerProfileId") UUID partnerProfileId);
}

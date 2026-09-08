package com.optimisante.backend.domain.training.finance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
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
}

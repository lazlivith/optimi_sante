package com.optimisante.backend.domain.document.recu;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentReceiptRepository extends JpaRepository<PaymentReceipt, UUID> {

    Optional<PaymentReceipt> findByReference(String reference);

    List<PaymentReceipt> findByBeneficiaireUserIdOrderByEmisLeDesc(UUID beneficiaireUserId);

    List<PaymentReceipt> findByEnrollmentIdOrderByEmisLeDesc(UUID enrollmentId);
}

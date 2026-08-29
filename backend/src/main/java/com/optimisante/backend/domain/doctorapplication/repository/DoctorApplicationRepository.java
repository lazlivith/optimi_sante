package com.optimisante.backend.domain.doctorapplication.repository;

import com.optimisante.backend.domain.doctorapplication.entity.DoctorApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DoctorApplicationRepository extends JpaRepository<DoctorApplication, UUID> {
    Optional<DoctorApplication> findByStripeCheckoutSessionId(String stripeCheckoutSessionId);
}

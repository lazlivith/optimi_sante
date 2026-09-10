package com.optimisante.backend.domain.training.repository;

import com.optimisante.backend.domain.training.entity.EnrollmentServiceOption;
import com.optimisante.backend.domain.training.entity.ServiceOptionStatus;
import com.optimisante.backend.domain.training.entity.ServiceOptionType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface EnrollmentServiceOptionRepository
        extends JpaRepository<EnrollmentServiceOption, UUID> {

    List<EnrollmentServiceOption> findByEnrollmentIdOrderBySelectedAtAsc(UUID enrollmentId);

    List<EnrollmentServiceOption> findByEnrollmentIdAndStatus(
            UUID enrollmentId, ServiceOptionStatus status);

    boolean existsByEnrollmentIdAndOptionTypeAndStatusIn(
            UUID enrollmentId, ServiceOptionType optionType, Collection<ServiceOptionStatus> statuses);

    /** Les services couverts par un encaissement donne : base de l'attestation. */
    List<EnrollmentServiceOption> findByPaymentId(UUID paymentId);
}

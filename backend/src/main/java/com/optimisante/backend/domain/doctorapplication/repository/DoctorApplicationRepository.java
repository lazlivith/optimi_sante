package com.optimisante.backend.domain.doctorapplication.repository;

import com.optimisante.backend.domain.doctorapplication.entity.DoctorApplication;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DoctorApplicationRepository extends JpaRepository<DoctorApplication, UUID> {
    Optional<DoctorApplication> findByStripeCheckoutSessionId(String stripeCheckoutSessionId);

    /**
     * Lit la candidature en la verrouillant jusqu'à la fin de la transaction.
     *
     * <p>La confirmation d'un paiement peut désormais partir de deux endroits à la fois : le
     * webhook Stripe et la page de retour du candidat. Sans verrou, les deux lisent
     * « PENDING_PAYMENT » au même instant et créent chacun le compte médecin — le second échoue
     * sur l'unicité de l'e-mail, au milieu d'une transaction qui a déjà écrit. Avec le verrou,
     * le second attend, relit « PAID », et s'arrête.</p>
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from DoctorApplication a where a.id = :id")
    Optional<DoctorApplication> findByIdForUpdate(@Param("id") UUID id);
}

package com.optimisante.backend.domain.marketing.repository;

import com.optimisante.backend.domain.marketing.entity.NewsletterSubscriber;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface NewsletterSubscriberRepository extends JpaRepository<NewsletterSubscriber, UUID> {

    /** Abonnement actif pour cette adresse, insensible a la casse. */
    Optional<NewsletterSubscriber> findByEmailIgnoreCaseAndUnsubscribedAtIsNull(String email);
}

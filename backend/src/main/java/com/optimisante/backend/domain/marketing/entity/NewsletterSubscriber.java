package com.optimisante.backend.domain.marketing.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Inscription a la lettre d'information, avec la preuve du consentement donne. */
@Entity
@Table(name = "newsletter_subscribers")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class NewsletterSubscriber {

    @Id
    @GeneratedValue(generator = "uuid2")
    @UuidGenerator
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false)
    private String email;

    @Column(name = "consent_given_at", nullable = false)
    private OffsetDateTime consentGivenAt;

    /**
     * Libelle exact accepte au moment de l'inscription.
     *
     * <p>Enregistrer une case cochee ne prouve rien : il faut savoir a quoi la personne a
     * consenti. Ce texte reste fige meme si le libelle affiche evolue.</p>
     */
    @Column(name = "consent_text", nullable = false, columnDefinition = "TEXT")
    private String consentText;

    @Column(nullable = false, length = 50)
    @Builder.Default
    private String source = "FOOTER";

    @Column(name = "unsubscribed_at")
    private OffsetDateTime unsubscribedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
        if (consentGivenAt == null) consentGivenAt = OffsetDateTime.now();
    }
}

package com.optimisante.backend.common.email;

import jakarta.persistence.*;
import lombok.*;

import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * Trace d'un email envoyé (ou tenté). Ne contient volontairement AUCUN corps de message :
 * l'email d'identifiants transporte un mot de passe provisoire en clair — voir V24.
 */
@Entity
@Table(name = "email_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmailLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 255)
    private String recipient;

    @Column(nullable = false, length = 500)
    private String subject;

    @Enumerated(EnumType.STRING)
    @Column(name = "email_type", nullable = false, length = 50)
    private EmailType emailType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EmailStatus status;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /**
     * Identifiant du compte destinataire, SANS clé étrangère vers `users` (voir V25) :
     * la trace est écrite dans une transaction indépendante qui ne verrait pas un compte
     * tout juste créé et non encore commité. Résolu à la lecture, peut donc pointer vers
     * un compte supprimé — auquel cas le renvoi est simplement indisponible.
     */
    @Column(name = "recipient_user_id")
    private UUID recipientUserId;

    @Column(name = "sent_at", nullable = false)
    private ZonedDateTime sentAt;

    @PrePersist
    void onPersist() {
        if (sentAt == null) sentAt = ZonedDateTime.now();
    }
}

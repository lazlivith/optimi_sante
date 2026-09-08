package com.optimisante.backend.common.email;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Écrit les traces d'emails dans une transaction INDÉPENDANTE.
 *
 * ⚠️ Pourquoi une classe séparée avec REQUIRES_NEW — bug réel constaté en recette :
 * la trace d'un envoi en ÉCHEC était bien insérée, puis immédiatement annulée par le
 * rollback de la transaction appelante, déclenché par l'exception que cette trace était
 * précisément censée documenter. Résultat : zéro échec visible dans le journal, alors que
 * la visibilité sur les échecs est toute la raison d'être de la fonctionnalité.
 *
 * Le même piège vaut pour les envois réussis : si le flux métier appelant échoue plus loin
 * (validation de partenariat, candidature médecin...), l'email est bel et bien parti mais
 * sa trace disparaîtrait avec le rollback. Une transaction dédiée règle les deux cas.
 *
 * La méthode doit vivre dans un bean distinct : Spring applique @Transactional via un proxy,
 * donc un appel interne à une méthode privée de la même classe serait sans effet.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailLogWriter {

    private static final int MAX_ERROR_LENGTH = 4000;

    private final EmailLogRepository emailLogRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String recipient, String subject, EmailType type, EmailStatus status,
                       String errorMessage, UUID recipientUserId) {
        try {
            emailLogRepository.save(EmailLog.builder()
                    .recipient(recipient)
                    .subject(subject)
                    .emailType(type)
                    .status(status)
                    .errorMessage(truncate(errorMessage))
                    .recipientUserId(recipientUserId)
                    .build());
        } catch (Exception e) {
            // Une défaillance du journal ne doit jamais transformer un envoi réussi en
            // erreur métier, ni masquer l'erreur d'envoi d'origine.
            log.warn("Impossible d'enregistrer la trace de l'email destiné à {} : {}",
                    recipient, e.getMessage());
        }
    }

    private String truncate(String message) {
        if (message == null) return null;
        return message.length() > MAX_ERROR_LENGTH ? message.substring(0, MAX_ERROR_LENGTH) : message;
    }
}

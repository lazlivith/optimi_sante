package com.optimisante.backend.domain.marketing.service;

import com.optimisante.backend.domain.marketing.dto.NewsletterSubscribeRequestDto;
import com.optimisante.backend.domain.marketing.entity.NewsletterSubscriber;
import com.optimisante.backend.domain.marketing.repository.NewsletterSubscriberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class NewsletterService {

    private final NewsletterSubscriberRepository repository;

    /**
     * Inscrit une adresse, ou rafraichit le consentement d'un abonnement deja actif.
     *
     * <p><b>Ré-inscrire une adresse déjà abonnée renvoie un succès</b>, sans erreur. Répondre
     * « cette adresse est déjà inscrite » transformerait le formulaire en oracle : n'importe
     * qui pourrait tester une liste d'adresses pour savoir lesquelles sont clientes. Le
     * consentement est simplement réenregistré avec sa date.</p>
     */
    @Transactional
    public void subscribe(NewsletterSubscribeRequestDto dto) {
        String email = dto.getEmail().trim();

        var existant = repository.findByEmailIgnoreCaseAndUnsubscribedAtIsNull(email);
        if (existant.isPresent()) {
            NewsletterSubscriber abonne = existant.get();
            abonne.setConsentGivenAt(OffsetDateTime.now());
            abonne.setConsentText(dto.getConsentText().trim());
            repository.save(abonne);
            log.info("Consentement newsletter rafraîchi");
            return;
        }

        repository.save(NewsletterSubscriber.builder()
                .email(email)
                .consentText(dto.getConsentText().trim())
                .consentGivenAt(OffsetDateTime.now())
                .source("FOOTER")
                .build());
        log.info("Nouvelle inscription à la lettre d'information");
    }
}

package com.optimisante.backend.domain.marketing.controller;

import com.optimisante.backend.domain.marketing.dto.NewsletterSubscribeRequestDto;
import com.optimisante.backend.domain.marketing.service.NewsletterService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Inscription a la lettre d'information depuis le pied de page.
 *
 * <p>Endpoint public : il s'adresse a des visiteurs non connectes, c'est tout son objet. La
 * reponse est volontairement identique pour une adresse nouvelle et pour une adresse deja
 * abonnee — distinguer les deux permettrait de savoir qui est client.</p>
 */
@RestController
@RequestMapping("/api/v1/newsletter")
@RequiredArgsConstructor
public class NewsletterResource {

    private final NewsletterService newsletterService;

    @PostMapping("/subscribe")
    public ResponseEntity<Map<String, String>> subscribe(
            @Valid @RequestBody NewsletterSubscribeRequestDto request) {
        newsletterService.subscribe(request);
        return ResponseEntity.ok(Map.of(
                "message", "Merci, votre inscription est enregistrée."));
    }
}

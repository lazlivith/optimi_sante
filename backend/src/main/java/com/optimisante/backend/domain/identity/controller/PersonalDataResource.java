package com.optimisante.backend.domain.identity.controller;

import com.optimisante.backend.domain.identity.dto.PersonalDataDto;
import com.optimisante.backend.domain.identity.service.PersonalDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Droit d'accès et de portabilité (RGPD, articles 15 et 20).
 *
 * <p>Aucune route ne prend d'identifiant : le service lit l'utilisateur authentifié. Exposer
 * {@code /personal-data/{userId}} aurait suffi à transformer cette page en outil d'extraction
 * de la base — le contrôle d'accès ne doit pas reposer sur la discrétion de l'appelant.</p>
 *
 * <p>Ouvert à tout compte authentifié, et pas seulement aux clients : le droit d'accès ne
 * dépend pas du rôle. Un médecin ou un partenaire y a droit autant qu'un acheteur.</p>
 */
@RestController
@RequestMapping("/api/v1/me/personal-data")
@RequiredArgsConstructor
public class PersonalDataResource {

    private final PersonalDataService personalDataService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PersonalDataDto> myData() {
        return ResponseEntity.ok(personalDataService.collectForCurrentUser());
    }

    @GetMapping("/export.csv")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> exportCsv() {
        byte[] csv = personalDataService.exportCsvForCurrentUser();
        String nom = "optimisante-donnees-personnelles-" + LocalDate.now() + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nom + "\"")
                .contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
                .body(csv);
    }
}

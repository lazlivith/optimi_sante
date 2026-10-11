package com.optimisante.backend.domain.exercerenfrance.web;

import com.optimisante.backend.config.security.MobilityAdmin;
import com.optimisante.backend.domain.exercerenfrance.dto.ExercerEnFranceDtos.*;
import com.optimisante.backend.domain.exercerenfrance.entity.AccompanimentRequest;
import com.optimisante.backend.domain.exercerenfrance.service.ServiceDemandesAccompagnement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Suivi des demandes d'accompagnement.
 *
 * <p>Entièrement sous {@code @MobilityAdmin} : traiter une demande est un acte métier, pas un
 * acte de gouvernance. Les prix, eux, restent ailleurs et sous une autre garde.</p>
 */
@RestController
@RequestMapping("/api/v1/admin/demandes-accompagnement")
@RequiredArgsConstructor
public class AdminDemandesAccompagnementResource {

    private final ServiceDemandesAccompagnement service;

    @GetMapping
    @MobilityAdmin
    public ResponseEntity<Page<AccompanimentRequest>> lister(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int taille) {
        return ResponseEntity.ok(service.lister(
                PageRequest.of(page, Math.min(taille, 100), Sort.by(Sort.Direction.DESC, "creeLe"))));
    }

    @GetMapping("/{id}")
    @MobilityAdmin
    public ResponseEntity<DemandeVue> detail(@PathVariable UUID id) {
        return ResponseEntity.ok(service.detail(id));
    }

    @PostMapping("/{id}/statut")
    @MobilityAdmin
    public ResponseEntity<Void> changerStatut(@PathVariable UUID id,
                                              @Valid @RequestBody ChangementStatutRequest dto,
                                              Authentication auth) {
        service.changerStatut(id, dto, utilisateur(auth));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/assignation")
    @MobilityAdmin
    public ResponseEntity<Void> assigner(@PathVariable UUID id,
                                         @RequestBody AssignationRequest dto,
                                         Authentication auth) {
        service.assigner(id, dto, utilisateur(auth));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/notes")
    @MobilityAdmin
    public ResponseEntity<Void> ajouterNote(@PathVariable UUID id,
                                            @Valid @RequestBody NoteRequest dto,
                                            Authentication auth) {
        service.ajouterNote(id, dto, utilisateur(auth));
        return ResponseEntity.noContent().build();
    }

    /**
     * Export CSV de la page courante.
     *
     * <p><b>Point-virgule et BOM.</b> Excel en configuration française découpe sur le
     * point-virgule et lit l'UTF-8 seulement s'il est annoncé : sans ces deux détails, le
     * fichier s'ouvre en une seule colonne avec les accents cassés, et c'est le premier
     * retour qu'on reçoit.</p>
     */
    @GetMapping(value = "/export.csv", produces = "text/csv")
    @MobilityAdmin
    public ResponseEntity<byte[]> exporter(@RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "500") int taille) {
        StringBuilder csv = new StringBuilder("﻿");
        csv.append("Date;Nom;Email;Téléphone;Pays;Profession;Spécialité;Étape;Offre;Statut\n");
        service.lister(PageRequest.of(page, Math.min(taille, 2000),
                        Sort.by(Sort.Direction.DESC, "creeLe")))
                .forEach(d -> csv.append(cellule(String.valueOf(d.getCreeLe()))).append(';')
                        .append(cellule(d.getNom())).append(';')
                        .append(cellule(d.getEmail())).append(';')
                        .append(cellule(d.getTelephoneWhatsapp())).append(';')
                        .append(cellule(d.getPays())).append(';')
                        .append(cellule(d.getProfession())).append(';')
                        .append(cellule(d.getSpecialite())).append(';')
                        .append(cellule(d.getEtape())).append(';')
                        .append(cellule(d.getOffreCle())).append(';')
                        .append(cellule(d.getStatut())).append('\n'));

        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"demandes-accompagnement.csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Neutralise une cellule.
     *
     * <p>Le guillemet doublé protège le découpage ; le guillemet simple en tête protège le
     * tableur : une valeur commençant par {@code =}, {@code +}, {@code -} ou {@code @} est
     * interprétée comme une FORMULE à l'ouverture. Un nom saisi sur la page publique
     * s'exécuterait alors sur le poste de l'administrateur.</p>
     */
    private static String cellule(String valeur) {
        if (valeur == null) {
            return "";
        }
        String v = valeur.replace("\"", "\"\"").replace("\n", " ").replace("\r", " ");
        if (!v.isEmpty() && "=+-@\t".indexOf(v.charAt(0)) >= 0) {
            v = "'" + v;
        }
        return "\"" + v + "\"";
    }

    private static UUID utilisateur(Authentication auth) {
        return auth == null ? null : UUID.fromString(auth.getPrincipal().toString());
    }
}

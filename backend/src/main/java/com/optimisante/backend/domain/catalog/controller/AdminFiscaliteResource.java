package com.optimisante.backend.domain.catalog.controller;

import com.optimisante.backend.config.security.EcommerceAdmin;
import com.optimisante.backend.domain.catalog.service.ServiceCampagnesPrix;
import com.optimisante.backend.domain.parametres.ServiceParametres;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Ce que l'administration du négoce pilote elle-même : l'affichage de la TVA et les révisions
 * tarifaires.
 *
 * <p>Deux leviers qui demandaient jusqu'ici un développeur — une variable d'environnement pour
 * le premier, une migration SQL écrite à la main pour le second. Ce sont pourtant des
 * décisions commerciales, prises par des gens qui n'ont ni les accès de l'hébergeur ni de
 * raison d'attendre un déploiement.</p>
 *
 * <p>Garde {@link EcommerceAdmin} : la TVA du catalogue et ses prix relèvent du négoce.</p>
 */
@RestController
@RequestMapping("/api/v1/admin/fiscalite")
@RequiredArgsConstructor
@EcommerceAdmin
public class AdminFiscaliteResource {

    private final ServiceParametres serviceParametres;
    private final ServiceCampagnesPrix serviceCampagnesPrix;

    // ── L'affichage de la TVA ───────────────────────────────────────────────────────────

    @GetMapping("/tva/affichage")
    public ResponseEntity<Map<String, Boolean>> lireAffichageTva() {
        return ResponseEntity.ok(Map.of(
                "actif", serviceParametres.estActif(ServiceParametres.TVA_ACTIVE, false)));
    }

    /**
     * Ouvre ou ferme la ventilation de la TVA sur les documents.
     *
     * <p>Ne change aucun montant : les prix sont annoncés TTC, la taxe est extraite dans les
     * deux cas. Seul l'affichage du détail sur les reçus et les devis est en jeu.</p>
     */
    @PutMapping("/tva/affichage")
    public ResponseEntity<Map<String, Boolean>> definirAffichageTva(
            @Valid @RequestBody BasculeRequest demande) {
        return ResponseEntity.ok(Map.of(
                "actif", serviceParametres.definir(ServiceParametres.TVA_ACTIVE, demande.isActif())));
    }

    // ── Les révisions tarifaires ────────────────────────────────────────────────────────

    /** Ce que la révision changerait, sans rien changer. */
    @GetMapping("/prix/apercu")
    public ResponseEntity<ServiceCampagnesPrix.Apercu> apercu(
            @RequestParam BigDecimal pourcentage,
            @RequestParam(required = false) UUID categorieId) {
        return ResponseEntity.ok(serviceCampagnesPrix.apercu(pourcentage, categorieId));
    }

    @PostMapping("/prix/revisions")
    public ResponseEntity<Map<String, String>> appliquer(@Valid @RequestBody RevisionRequest demande) {
        UUID id = serviceCampagnesPrix.appliquer(
                demande.getPourcentage(), demande.getCategorieId(), demande.getLibelle());
        return ResponseEntity.ok(Map.of("id", id.toString()));
    }

    @GetMapping("/prix/revisions")
    public ResponseEntity<List<ServiceCampagnesPrix.Campagne>> historique() {
        return ResponseEntity.ok(serviceCampagnesPrix.historique());
    }

    /** Restitue les prix d'avant la révision. */
    @PostMapping("/prix/revisions/{id}/annulation")
    public ResponseEntity<Map<String, Integer>> annuler(@PathVariable UUID id) {
        return ResponseEntity.ok(Map.of("prixRestaures", serviceCampagnesPrix.annuler(id)));
    }

    @Data
    public static class BasculeRequest {
        private boolean actif;
    }

    @Data
    public static class RevisionRequest {
        @NotNull(message = "Indiquez un pourcentage.")
        private BigDecimal pourcentage;
        /** Nulle : la révision porte sur tout le catalogue. */
        private UUID categorieId;
        private String libelle;
    }
}

package com.optimisante.backend.domain.finance;

import com.optimisante.backend.config.security.PlatformAdmin;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * Les devises de présentation et leurs taux.
 *
 * <p>Réservé au super administrateur : un taux de change ne relève ni du négoce ni de la
 * mobilité, il décide du prix affiché sur <b>toute</b> la plateforme — catalogue, formations,
 * options de service. D'où {@code @PlatformAdmin} plutôt qu'une des deux annotations métier.</p>
 */
@RestController
@RequestMapping("/api/v1/admin/devises")
@RequiredArgsConstructor
@PlatformAdmin
public class DevisesResource {

    private final ServiceDevises serviceDevises;

    @GetMapping
    public ResponseEntity<List<DeviseDto>> grille() {
        return ResponseEntity.ok(serviceDevises.grille().stream()
                .map(t -> new DeviseDto(t.getDevise(), t.getTaux(), t.getPalierArrondi(),
                        Boolean.TRUE.equals(t.getActif()),
                        Devise.de(t.getDevise()).estReference(),
                        Devise.de(t.getDevise()).decimales()))
                .toList());
    }

    @PutMapping("/{code}")
    public ResponseEntity<DeviseDto> enregistrer(@PathVariable String code,
                                                 @Valid @RequestBody TauxRequest demande) {
        Devise devise = Devise.de(code);
        TauxChange enregistre = serviceDevises.enregistrer(
                devise, demande.getTaux(), demande.getPalierArrondi(), demande.isActif());
        return ResponseEntity.ok(new DeviseDto(
                enregistre.getDevise(), enregistre.getTaux(), enregistre.getPalierArrondi(),
                Boolean.TRUE.equals(enregistre.getActif()), devise.estReference(),
                devise.decimales()));
    }

    /**
     * Un aperçu de ce que donnerait la conversion, sans rien enregistrer.
     *
     * <p>L'écran s'en sert pour montrer le prix réellement affiché avant de valider un taux :
     * un palier mal choisi se voit immédiatement sur un montant parlant, et non à la première
     * commande.</p>
     */
    @GetMapping("/{code}/apercu")
    public ResponseEntity<ApercuDto> apercu(@PathVariable String code,
                                            @RequestParam BigDecimal montant) {
        Devise devise = Devise.de(code);
        Montant converti = serviceDevises.convertir(Montant.euros(montant), devise);
        return ResponseEntity.ok(new ApercuDto(montant, converti.valeur(), devise.code()));
    }

    /**
     * @param reference vrai pour l'euro, que l'écran affiche sans permettre de le modifier
     * @param decimales nombre de décimales de la devise, pour la saisie et l'affichage
     */
    public record DeviseDto(String devise, BigDecimal taux, BigDecimal palierArrondi,
                            boolean actif, boolean reference, int decimales) {}

    public record ApercuDto(BigDecimal montantEuros, BigDecimal montantConverti, String devise) {}

    @Data
    public static class TauxRequest {
        @NotNull(message = "Le taux est obligatoire.")
        private BigDecimal taux;

        @NotNull(message = "Le palier d'arrondi est obligatoire.")
        private BigDecimal palierArrondi;

        private boolean actif;
    }
}

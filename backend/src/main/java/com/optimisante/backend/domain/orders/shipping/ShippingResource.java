package com.optimisante.backend.domain.orders.shipping;

import com.optimisante.backend.config.security.EcommerceAdmin;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

/**
 * La grille de livraison : consultable par la boutique, modifiable par l'administration.
 *
 * <p>Deux lectures publiques, parce que le client doit connaître le coût <b>avant</b> de
 * valider — c'est ce que les conditions générales promettent. Et une écriture réservée à
 * l'administration du négoce : un tarif de transport est une décision commerciale.</p>
 */
@RestController
@RequiredArgsConstructor
public class ShippingResource {

    private final ServiceLivraison serviceLivraison;

    /** Les destinations desservies, pour alimenter le choix du pays au moment de commander. */
    @GetMapping("/api/v1/shipping/destinations")
    public ResponseEntity<List<DestinationDto>> destinations() {
        return ResponseEntity.ok(Arrays.stream(ZoneLivraison.values())
                .flatMap(z -> z.pays().stream().map(p -> new DestinationDto(p, z.name(), z.libelle())))
                .sorted((a, b) -> a.codePays().compareTo(b.codePays()))
                .toList());
    }

    /**
     * Ce que coûterait la livraison vers ce pays, pour ce montant d'articles.
     *
     * <p>Appelé pendant que le client remplit son adresse : il voit le total se mettre à jour
     * au lieu de le découvrir à la validation.</p>
     */
    @GetMapping("/api/v1/shipping/estimation")
    public ResponseEntity<EstimationDto> estimer(@RequestParam String pays,
                                                 @RequestParam(required = false) BigDecimal montant) {
        var frais = serviceLivraison.calculer(pays, montant == null ? BigDecimal.ZERO : montant);
        return ResponseEntity.ok(new EstimationDto(
                frais.zone().name(), frais.zone().libelle(), frais.montant(), frais.offerte(),
                // Les expeditions hors de France sont livrees DAP : le destinataire acquitte
                // droits et taxes a l'arrivee. Le dire ici, et non a la livraison.
                frais.zone() != ZoneLivraison.FRANCE));
    }

    @GetMapping("/api/v1/admin/shipping-rates")
    @EcommerceAdmin
    public ResponseEntity<List<TarifDto>> grille() {
        return ResponseEntity.ok(serviceLivraison.grille().stream()
                .map(t -> new TarifDto(t.getZone().name(), t.getZone().libelle(), t.getAmount(),
                        t.getFreeFrom(), t.getIsActive()))
                .toList());
    }

    @PutMapping("/api/v1/admin/shipping-rates/{zone}")
    @EcommerceAdmin
    public ResponseEntity<TarifDto> enregistrer(@PathVariable ZoneLivraison zone,
                                                @Valid @RequestBody TarifRequest demande) {
        var tarif = serviceLivraison.enregistrer(zone, demande.getAmount(), demande.getFreeFrom(),
                demande.isActive());
        return ResponseEntity.ok(new TarifDto(tarif.getZone().name(), tarif.getZone().libelle(),
                tarif.getAmount(), tarif.getFreeFrom(), tarif.getIsActive()));
    }

    public record DestinationDto(String codePays, String zone, String zoneLibelle) {}

    /** @param droitsALArrivee le destinataire acquitte droits et taxes a l'arrivee (DAP) */
    public record EstimationDto(String zone, String zoneLibelle, BigDecimal montant,
                                boolean offerte, boolean droitsALArrivee) {}

    public record TarifDto(String zone, String libelle, BigDecimal amount, BigDecimal freeFrom,
                           boolean active) {}

    @Data
    public static class TarifRequest {
        @NotNull(message = "Le tarif est obligatoire.")
        private BigDecimal amount;
        /** Montant de commande a partir duquel la livraison est offerte. Vide : jamais. */
        private BigDecimal freeFrom;
        private boolean active = true;
    }
}

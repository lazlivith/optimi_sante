package com.optimisante.backend.domain.catalog.controller;

import com.optimisante.backend.domain.catalog.service.ServiceVentilationPanier;
import jakarta.validation.Valid;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * La taxe contenue dans un panier, avant que la commande n'existe.
 *
 * <p>Public : un visiteur remplit son panier avant d'avoir un compte, et doit voir le detail
 * de la taxe comme n'importe quel client. Le serveur ne divulgue rien de sensible — la
 * ventilation d'un panier que l'appelant a lui-meme compose.</p>
 *
 * <p>En POST et non en GET : un panier peut contenir des dizaines de lignes, et les faire
 * tenir dans une URL imposerait une limite arbitraire la ou le corps de requete n'en a pas.</p>
 */
@RestController
@RequiredArgsConstructor
public class TvaPanierResource {

    private final ServiceVentilationPanier service;

    @PostMapping("/api/v1/catalog/tva/panier")
    public ResponseEntity<ServiceVentilationPanier.Ventilation> ventiler(
            @Valid @RequestBody PanierRequest demande) {

        List<ServiceVentilationPanier.LignePanier> lignes = demande.getItems() == null
                ? List.of()
                : demande.getItems().stream()
                        .map(i -> new ServiceVentilationPanier.LignePanier(
                                i.getProductId(), i.getQuantity()))
                        .toList();

        return ResponseEntity.ok(service.ventiler(lignes, demande.getRemise()));
    }

    @Data
    public static class PanierRequest {
        private List<Ligne> items;
        /** Remise deja accordee sur le panier, repartie au prorata des lignes. */
        private BigDecimal remise;

        @Data
        public static class Ligne {
            private UUID productId;
            private int quantity;
        }
    }
}

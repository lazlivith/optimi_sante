package com.optimisante.backend.domain.orders.webhook;

import com.optimisante.backend.domain.orders.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Remboursement d'une commande de la boutique. */
@Component
@RequiredArgsConstructor
public class RemboursementCommande implements TraitementRemboursement {

    private final OrderService orderService;

    @Override
    public boolean appliquer(RemboursementConstate remboursement) {
        return orderService.constaterRemboursement(remboursement);
    }
}

package com.optimisante.backend.domain.orders.dto;

import com.optimisante.backend.domain.orders.entity.PaymentMethod;

import java.util.List;

/**
 * Ce que le client valide au moment de payer.
 *
 * <p>L'adresse de livraison est facultative dans le contrat : les demandes de devis n'en ont
 * pas toujours une, et les appels existants restent valables. Renseignee, elle determine la
 * zone et donc les frais de port ; absente, la commande ne supporte aucun frais — ce qui est
 * le comportement d'avant.</p>
 *
 * @param shippingCountry code ISO 3166-1 alpha-2 du pays de destination
 */
public record CheckoutRequestDto(
        List<CheckoutItemDto> items,
        PaymentMethod paymentMethod,
        String promoCode,
        String shippingRecipient,
        String shippingLine1,
        String shippingLine2,
        String shippingPostalCode,
        String shippingCity,
        String shippingCountry
) {}

package com.optimisante.backend.domain.orders.dto;

import com.optimisante.backend.domain.orders.entity.OrderStatus;
import com.optimisante.backend.domain.orders.entity.PaymentMethod;
import com.optimisante.backend.domain.orders.entity.PaymentStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record OrderResponseDto(
        UUID id,
        String orderNumber,
        PaymentMethod paymentMethod,
        PaymentStatus paymentStatus,
        OrderStatus status,
        Boolean isQuote,
        BigDecimal totalAmount,
        String stripePaymentIntentId,
        String stripeCheckoutSessionId,
        String paymentUrl,
        String clientSecret,
        /**
         * Un reçu (ou devis) existe-t-il pour cette commande ?
         *
         * <p><b>Un booléen, et non la clé de stockage (DP-4).</b> La clé était le {@code public_id}
         * Cloudinary du document : exposée au navigateur, elle ouvrait un accès direct à un objet
         * alors public, hors de tout contrôle de rôle. Le téléchargement passe par
         * {@code GET /api/v1/documents/{type}/{id}/download}, qui vérifie le rôle et rend une URL à
         * jeton. L'interface n'a besoin que de savoir s'il faut afficher le bouton.</p>
         */
        boolean hasDocument,
        String promoCode,
        BigDecimal discountAmount,
        /** Remise accordée par l'administration sur un devis, en pourcentage (V59). Nulle sinon. */
        BigDecimal quoteDiscountRate,
        OffsetDateTime createdAt,
        /**
         * Ce qui a réellement été débité, et dans quelle monnaie.
         *
         * <p>{@code totalAmount} reste en euros : c'est la devise de référence. Ces deux champs
         * disent ce que le client a vu et payé, pour que son historique de commandes annonce
         * le même montant que son relevé bancaire et que son reçu (V66).</p>
         */
        String paymentCurrency,
        BigDecimal paymentAmount,
        /** Montant cumulé rendu au client. Zéro pour une commande non remboursée (V65). */
        BigDecimal refundedAmount,
        List<OrderItemDto> items
) {}

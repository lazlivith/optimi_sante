package com.optimisante.backend.domain.orders.webhook;

import com.stripe.model.checkout.Session;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Aiguille un paiement confirmé vers le traitement correspondant à son usage.
 *
 * <p>Le contrôleur de webhook ne connaît plus aucun parcours métier : ajouter un mode de
 * paiement consiste à déclarer un {@link StripePaymentHandler}, sans toucher au point
 * d'entrée ni aux parcours déjà en production.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StripePaymentDispatcher {

    /** Nouvelle clé de métadonnée portant l'usage du paiement. */
    private static final String METADATA_PAYMENT_TYPE = "payment_type";
    /** Clé historique, posée par la candidature médecin depuis la Phase 3. */
    private static final String METADATA_LEGACY_TYPE = "type";

    private final List<StripePaymentHandler> handlers;

    /**
     * Résout l'usage d'une session.
     *
     * <p>Ordre volontaire : nouvelle clé, puis clé historique, puis commande e-commerce
     * par défaut. Les sessions e-commerce ne portent aucune métadonnée — sans ce défaut,
     * le parcours d'achat le plus utilisé de la plateforme cesserait d'être traité.</p>
     */
    public String resolvePaymentType(Session session) {
        Map<String, String> metadata = session.getMetadata();
        if (metadata != null) {
            String type = metadata.get(METADATA_PAYMENT_TYPE);
            if (type != null && !type.isBlank()) {
                return type;
            }
            String legacy = metadata.get(METADATA_LEGACY_TYPE);
            if (legacy != null && !legacy.isBlank()) {
                return legacy;
            }
        }
        return ProductCheckoutStripeHandler.PAYMENT_TYPE;
    }

    public void dispatch(UUID referenceId, Session session) {
        String paymentType = resolvePaymentType(session);

        StripePaymentHandler handler = handlers.stream()
                .filter(h -> h.supports(paymentType))
                .findFirst()
                .orElse(null);

        if (handler == null) {
            // Cas volontairement bruyant : un usage inconnu signifie qu'une session a été
            // créée avec une métadonnée non gérée. On préfère un log explicite à un
            // traitement par défaut qui confirmerait la mauvaise commande.
            log.error("Aucun traitement déclaré pour le type de paiement '{}' (référence {}). "
                    + "Paiement encaissé mais non traité côté plateforme.", paymentType, referenceId);
            return;
        }

        handler.handle(referenceId, session);
    }
}

package com.optimisante.backend.domain.orders.controller;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Charge;
import com.stripe.model.Event;
import com.stripe.exception.EventDataObjectDeserializationException;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.StripeObject;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import com.optimisante.backend.domain.orders.webhook.StripePaymentDispatcher;
import com.optimisante.backend.domain.orders.webhook.StripeRefundHandler;

@Slf4j
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentWebhookResource {

    private final StripePaymentDispatcher stripePaymentDispatcher;
    private final StripeRefundHandler stripeRefundHandler;

    @Value("${stripe.webhook-secret}")
    private String endpointSecret;

    @PostMapping("/webhook")
    public ResponseEntity<String> handleStripeWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader) {

        Event event;

        try {
            // Verify signature using the Stripe CLI webhook secret
            event = Webhook.constructEvent(payload, sigHeader, endpointSecret);
        } catch (SignatureVerificationException e) {
            log.error("Stripe webhook signature verification failed.", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Signature Verification Failed");
        } catch (Exception e) {
            log.error("Stripe webhook parsing failed.", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Payload Parsing Failed");
        }

        // Un événement non traité n'est pas une erreur : Stripe en émet beaucoup, et répondre
        // autre chose que 200 le ferait rejouer indéfiniment.
        if ("charge.refunded".equals(event.getType())) {
            return traiterRemboursement(event);
        }

        // Handle the checkout.session.completed event
        if ("checkout.session.completed".equals(event.getType())) {
            // Meme lecture permissive que pour le remboursement : un ecart de version d'API
            // faisait abandonner l'evenement, donc un paiement encaisse dont la commande
            // restait non confirmee. Le chemin strict reste premier ; rien ne change quand il
            // aboutit.
            StripeObject stripeObject = objetDeLEvenement(event);
            {
                if (stripeObject instanceof Session session) {
                    
                    String clientReferenceId = session.getClientReferenceId();

                    if (clientReferenceId != null) {
                        try {
                            UUID referenceId = UUID.fromString(clientReferenceId);
                            // Le contrôleur ne connaît plus aucun parcours métier : l'usage est
                            // résolu depuis les métadonnées de la session et aiguillé vers le
                            // StripePaymentHandler correspondant. Ajouter un mode de paiement
                            // n'impose plus de modifier ce point d'entrée.
                            stripePaymentDispatcher.dispatch(referenceId, session);
                        } catch (IllegalArgumentException e) {
                            log.error("Invalid clientReferenceId format received from Stripe: {}", clientReferenceId);
                        } catch (Exception e) {
                            log.error("Failed to process payment confirmation.", e);
                            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
                        }
                    } else {
                        log.warn("Stripe Checkout Session completed without client_reference_id.");
                    }
                }
            }
        }

        return ResponseEntity.ok("Success");
    }

    /**
     * Remboursement constaté chez Stripe.
     *
     * <p>Rembourser depuis le tableau de bord Stripe ne produisait aucun effet ici : la commande
     * restait marquée payée et continuait de peser dans le chiffre d'affaires.</p>
     *
     * <p>Répond 200 même lorsque aucune commande ne correspond : l'événement a bien été reçu et
     * compris. Un autre code ferait rejouer Stripe toutes les heures pendant trois jours pour un
     * remboursement qui relève d'un parcours que ce code ne traite pas encore.</p>
     */
    private ResponseEntity<String> traiterRemboursement(Event event) {
        StripeObject objet = objetDeLEvenement(event);
        if (!(objet instanceof Charge charge)) {
            log.warn("Événement charge.refunded sans objet Charge exploitable (id {}).",
                    event.getId());
            return ResponseEntity.ok("Ignored");
        }

        try {
            stripeRefundHandler.handle(charge);
        } catch (Exception e) {
            // Un 500 ferait rejouer Stripe, ce qui est souhaitable sur une panne passagère.
            log.error("Traitement du remboursement {} impossible.", charge.getId(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
        return ResponseEntity.ok("Success");
    }

    /**
     * L'objet porté par un événement, y compris quand les versions d'API ne concordent pas.
     *
     * <p><b>Ce repli n'est pas une précaution théorique.</b> Stripe fige la version d'API d'un
     * compte, et la bibliothèque Java en vise une autre : dès qu'elles divergent — un compte
     * créé à une autre date, une mise à jour de la bibliothèque — la désérialisation stricte
     * rend un {@code Optional} vide. L'événement était alors journalisé puis <b>abandonné</b> :
     * un remboursement réel passait inaperçu, et la commande restait marquée payée. Éprouvé en
     * local : un événement portant une version d'API différente n'arrivait pas au traitement.</p>
     *
     * <p>{@code deserializeUnsafe} est la réponse documentée par Stripe à ce cas. Elle est dite
     * « unsafe » parce qu'un champ renommé entre deux versions peut manquer — ce qui reste
     * préférable à ne rien traiter du tout, d'autant que les champs lus ici ({@code
     * payment_intent}, {@code amount_refunded}, {@code refunded}) sont stables de longue date.</p>
     */
    private StripeObject objetDeLEvenement(Event event) {
        EventDataObjectDeserializer deserialiseur = event.getDataObjectDeserializer();
        if (deserialiseur.getObject().isPresent()) {
            return deserialiseur.getObject().get();
        }
        try {
            log.info("Version d'API différente sur l'événement {} : lecture en mode permissif.",
                    event.getId());
            return deserialiseur.deserializeUnsafe();
        } catch (EventDataObjectDeserializationException e) {
            log.error("Objet de l'événement {} illisible, même en mode permissif.",
                    event.getId(), e);
            return null;
        }
    }
}

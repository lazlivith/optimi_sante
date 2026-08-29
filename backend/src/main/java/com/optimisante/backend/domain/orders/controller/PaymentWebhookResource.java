package com.optimisante.backend.domain.orders.controller;

import com.optimisante.backend.domain.doctorapplication.service.DoctorApplicationService;
import com.optimisante.backend.domain.orders.service.OrderService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
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

@Slf4j
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentWebhookResource {

    private final OrderService orderService;
    private final DoctorApplicationService doctorApplicationService;

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

        // Handle the checkout.session.completed event
        if ("checkout.session.completed".equals(event.getType())) {
            EventDataObjectDeserializer dataObjectDeserializer = event.getDataObjectDeserializer();
            if (dataObjectDeserializer.getObject().isPresent()) {
                StripeObject stripeObject = dataObjectDeserializer.getObject().get();
                if (stripeObject instanceof Session session) {
                    
                    String clientReferenceId = session.getClientReferenceId();
                    // "type" en métadonnée distingue les différents usages de Stripe Checkout sur
                    // la plateforme (commande e-commerce par défaut, candidature médecin payante...)
                    // sans jamais réinterpréter client_reference_id, qui reste l'identifiant "brut".
                    String referenceType = session.getMetadata() != null ? session.getMetadata().get("type") : null;

                    if (clientReferenceId != null) {
                        try {
                            UUID referenceId = UUID.fromString(clientReferenceId);
                            if ("DOCTOR_APPLICATION".equals(referenceType)) {
                                log.info("Received checkout.session.completed for Doctor Application ID: {}", referenceId);
                                doctorApplicationService.confirmPayment(referenceId, session.getCustomer());
                            } else {
                                log.info("Received checkout.session.completed for Order ID: {}", referenceId);
                                orderService.confirmOrderPayment(referenceId);
                            }
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
            } else {
                log.warn("Stripe Event missing Data Object.");
            }
        }

        return ResponseEntity.ok("Success");
    }
}

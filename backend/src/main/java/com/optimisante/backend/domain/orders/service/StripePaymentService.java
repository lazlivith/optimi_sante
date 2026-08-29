package com.optimisante.backend.domain.orders.service;

import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.checkout.Session;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.checkout.SessionCreateParams;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Service
public class StripePaymentService {

    @Value("${stripe.api-key}")
    private String stripeApiKey;

    @PostConstruct
    public void init() {
        Stripe.apiKey = stripeApiKey;
    }

    public Session createCheckoutSession(UUID orderId, BigDecimal amount, String userEmail, String successUrl, String cancelUrl) throws StripeException {
        return createCheckoutSession(orderId, amount, userEmail, successUrl, cancelUrl,
                "Commande Optimi Santé #" + orderId, null);
    }

    /**
     * Variante générique : permet de préciser le libellé de la ligne Stripe et des métadonnées
     * arbitraires (ex. "type": "DOCTOR_APPLICATION") lues par le webhook pour distinguer les
     * différents cas d'usage (commande e-commerce, candidature médecin, etc.) sans jamais
     * réutiliser client_reference_id à double sens.
     */
    public Session createCheckoutSession(UUID referenceId, BigDecimal amount, String userEmail, String successUrl,
                                          String cancelUrl, String productName, Map<String, String> metadata) throws StripeException {
        SessionCreateParams.Builder builder = SessionCreateParams.builder()
                .addPaymentMethodType(SessionCreateParams.PaymentMethodType.CARD)
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setCustomerEmail(userEmail)
                .setClientReferenceId(referenceId.toString())
                .setSuccessUrl(successUrl + "?session_id={CHECKOUT_SESSION_ID}")
                .setCancelUrl(cancelUrl)
                .addLineItem(
                        SessionCreateParams.LineItem.builder()
                                .setQuantity(1L)
                                .setPriceData(
                                        SessionCreateParams.LineItem.PriceData.builder()
                                                .setCurrency("eur")
                                                .setUnitAmount(amount.multiply(new BigDecimal(100)).longValue()) // Conversion en centimes
                                                .setProductData(
                                                        SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                                                .setName(productName)
                                                                .build()
                                                )
                                                .build()
                                )
                                .build()
                );

        if (metadata != null) {
            metadata.forEach(builder::putMetadata);
        }

        return Session.create(builder.build());
    }

    /**
     * Crée un Customer Stripe pour un email donné. Utilisé pour un utilisateur qui n'a pas
     * encore de compte au moment du paiement (ex. candidature médecin) : Stripe crée le
     * Customer à la volée pendant le Checkout via customer_creation=always, ce qui suffit —
     * cette méthode explicite reste disponible si un besoin de pré-création apparaît.
     */
    /**
     * Récupère l'état courant d'une Checkout Session depuis Stripe (utilisé par la page de
     * retour côté client après confirmation d'un paiement en mode "elements", pour savoir si le
     * paiement a réellement abouti — le webhook reste la source de vérité côté serveur/BDD).
     */
    public Session retrieveSession(String sessionId) throws StripeException {
        return Session.retrieve(sessionId);
    }

    public String createCustomer(String email) throws StripeException {
        Customer customer = Customer.create(CustomerCreateParams.builder().setEmail(email).build());
        return customer.getId();
    }

    /**
     * Session Checkout en mode "elements" (Payment Element intégré à la page, plus de
     * redirection vers checkout.stripe.com) pour un Customer Stripe déjà connu — permet la
     * réutilisation d'une carte enregistrée lors d'un achat précédent. À utiliser quand
     * l'utilisateur a déjà un compte (et donc potentiellement déjà un stripeCustomerId).
     */
    public Session createElementsCheckoutSessionForCustomer(UUID referenceId, BigDecimal amount, String customerId,
                                                              String returnUrl, String productName,
                                                              Map<String, String> metadata) throws StripeException {
        SessionCreateParams.Builder builder = baseElementsSessionBuilder(referenceId, amount, returnUrl, productName, metadata)
                .setCustomer(customerId);
        return Session.create(builder.build());
    }

    /**
     * Session Checkout en mode "elements" pour un client sans Customer Stripe préexistant
     * (ex. candidature médecin avant création de compte) : Stripe crée automatiquement un
     * nouveau Customer pendant le paiement (customer_creation=always). Son ID est récupérable
     * ensuite via session.getCustomer() une fois le paiement confirmé.
     */
    public Session createElementsCheckoutSessionForNewCustomer(UUID referenceId, BigDecimal amount, String email,
                                                                 String returnUrl, String productName,
                                                                 Map<String, String> metadata) throws StripeException {
        SessionCreateParams.Builder builder = baseElementsSessionBuilder(referenceId, amount, returnUrl, productName, metadata)
                .setCustomerEmail(email)
                .setCustomerCreation(SessionCreateParams.CustomerCreation.ALWAYS);
        return Session.create(builder.build());
    }

    private SessionCreateParams.Builder baseElementsSessionBuilder(UUID referenceId, BigDecimal amount, String returnUrl,
                                                                     String productName, Map<String, String> metadata) {
        SessionCreateParams.Builder builder = SessionCreateParams.builder()
                // "CUSTOM" est le nom de l'énum stripe-java pour ce que la doc Stripe appelle
                // désormais ui_mode "elements" (valeur réseau "custom" — API "Custom Checkout",
                // celle que cible @stripe/react-stripe-js CheckoutProvider/fetchClientSecret).
                .setUiMode(SessionCreateParams.UiMode.CUSTOM)
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setClientReferenceId(referenceId.toString())
                .setReturnUrl(returnUrl)
                // Active la case "Enregistrer ma carte" (Payment Element) et la réutilisation
                // des cartes déjà sauvegardées pour ce Customer, conformément à la doc Stripe
                // "Save customer payment methods".
                .setSavedPaymentMethodOptions(
                        SessionCreateParams.SavedPaymentMethodOptions.builder()
                                .setPaymentMethodSave(SessionCreateParams.SavedPaymentMethodOptions.PaymentMethodSave.ENABLED)
                                .build())
                .addLineItem(
                        SessionCreateParams.LineItem.builder()
                                .setQuantity(1L)
                                .setPriceData(
                                        SessionCreateParams.LineItem.PriceData.builder()
                                                .setCurrency("eur")
                                                .setUnitAmount(amount.multiply(new BigDecimal(100)).longValue())
                                                .setProductData(
                                                        SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                                                .setName(productName)
                                                                .build()
                                                )
                                                .build()
                                )
                                .build()
                );

        if (metadata != null) {
            metadata.forEach(builder::putMetadata);
        }
        return builder;
    }
}

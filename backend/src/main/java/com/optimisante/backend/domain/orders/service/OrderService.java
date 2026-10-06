package com.optimisante.backend.domain.orders.service;

import com.optimisante.backend.common.storage.DossierStockage;
import com.optimisante.backend.config.tenant.TenantContext;
import com.optimisante.backend.domain.catalog.entity.Product;
import com.optimisante.backend.domain.catalog.repository.ProductRepository;
import com.optimisante.backend.domain.catalog.service.StockReservationService;
import com.optimisante.backend.domain.catalog.repository.StockReservationRepository;
import com.optimisante.backend.domain.catalog.entity.StockReservation;
import com.optimisante.backend.domain.document.recu.Encaissement;
import com.optimisante.backend.domain.document.recu.MotifPaiement;
import com.optimisante.backend.domain.identity.entity.Role;
import com.optimisante.backend.domain.identity.entity.Tenant;
import com.optimisante.backend.domain.identity.entity.User;
import com.optimisante.backend.domain.identity.repository.CompanyProfileRepository;
import com.optimisante.backend.domain.identity.repository.TenantRepository;
import com.optimisante.backend.domain.identity.repository.UserRepository;
import com.optimisante.backend.domain.orders.dto.*;
import com.optimisante.backend.domain.orders.entity.*;
import com.optimisante.backend.domain.orders.repository.OrderRepository;
import com.optimisante.backend.domain.document.service.PdfGeneratorService;
import com.optimisante.backend.domain.promotion.service.PromoCodeService;
import com.stripe.model.checkout.Session;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.optimisante.backend.domain.finance.Devise;
import com.optimisante.backend.domain.finance.Montant;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {


    private final OrderRepository orderRepository;
    private final com.optimisante.backend.domain.document.recu.PaymentReceiptIssuer receiptIssuer;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final CompanyProfileRepository companyProfileRepository;
    private final com.optimisante.backend.domain.catalog.service.ServiceTva serviceTva;
    private final com.optimisante.backend.domain.orders.shipping.ServiceLivraison serviceLivraison;
    private final com.optimisante.backend.domain.finance.ServiceDevises serviceDevises;
    private final StockReservationService stockReservationService;
    private final StockReservationRepository stockReservationRepository;
    private final StripePaymentService stripePaymentService;
    private final PdfGeneratorService pdfGeneratorService;
    private final PromoCodeService promoCodeService;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;

    @org.springframework.beans.factory.annotation.Value("${app.mail.frontend-base-url}")
    private String frontendBaseUrl;

    @Transactional
    public OrderResponseDto createCheckoutOrder(CheckoutRequestDto request) {
        return processOrderCreation(request.items(), request.paymentMethod(), false, null,
                request.promoCode(), request);
    }

    @Transactional
    public OrderResponseDto createQuoteRequest(QuoteRequestDto request) {
        // Les codes promo ne s'appliquent pas aux devis B2B (négociation individuelle ensuite).
        // Pas d'adresse non plus : un devis se chiffre avant que la destination soit arretee,
        // et les frais de port seront ajoutes a la commande qui en decoulera.
        return processOrderCreation(request.items(), PaymentMethod.QUOTE_REQUEST, true,
                request.notes(), null, null);
    }

    private OrderResponseDto processOrderCreation(List<CheckoutItemDto> items, PaymentMethod paymentMethod, boolean isQuote, String notes, String promoCodeInput,
                                                  CheckoutRequestDto livraison) {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new IllegalStateException("Tenant context is required");
        }
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant not found"));

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new IllegalStateException("User must be authenticated to create an order");
        }
        UUID userId = UUID.fromString(auth.getPrincipal().toString());
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Un etablissement partenaire est aussi un client de la boutique : il achete du
        // materiel. Le taux lui-meme vit sur le profil d'entreprise — ce test n'est qu'un
        // portillon, et le restreindre au seul role B2B privait les partenaires d'une remise
        // pourtant negociee avec eux.
        boolean isB2B = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_" + Role.CLIENT_B2B.name())
                        || a.getAuthority().equals("ROLE_" + Role.CENTRE_FORMATION.name()));
        
        BigDecimal b2bDiscountRate = BigDecimal.ZERO;
        if (isB2B) {
            b2bDiscountRate = companyProfileRepository.findByUserId(userId)
                    .map(profile -> profile.getB2bDiscountRate())
                    .orElse(BigDecimal.ZERO);
        }

        PaymentStatus initialPaymentStatus = PaymentStatus.UNPAID;
        if (isQuote || paymentMethod == PaymentMethod.QUOTE_REQUEST) {
            initialPaymentStatus = PaymentStatus.QUOTE_SENT;
        }

        Order order = Order.builder()
                .tenant(tenant)
                .user(user)
                .orderNumber(generateOrderNumber())
                .paymentMethod(paymentMethod)
                .paymentStatus(initialPaymentStatus)
                .status(OrderStatus.PENDING)
                .isQuote(isQuote)
                .build();

        BigDecimal totalAmount = BigDecimal.ZERO;

        for (CheckoutItemDto itemDto : items) {
            Product product = productRepository.findByIdWithPessimisticLock(itemDto.productId())
                    .orElseThrow(() -> new RuntimeException("Product not found: " + itemDto.productId()));

            if (product.getIsQuoteOnly() && !isQuote) {
                throw new IllegalStateException("Product " + product.getSku() + " can only be ordered via Quote Request");
            }

            // Reserve stock for 30 minutes (or longer for quotes)
            int ttlMinutes = isQuote ? (24 * 60) : 30; 
            stockReservationService.reserveStock(product.getId(), userId, itemDto.quantity(), ttlMinutes);

            // Part du prix effectif (promotion produit active le cas échéant) — même point de
            // vérité que l'affichage catalogue (Product.getEffectiveBasePrice), pour ne jamais
            // facturer un montant différent de celui affiché au client.
            BigDecimal unitPrice = product.getEffectiveBasePrice();
            if (isB2B && b2bDiscountRate.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal multiplier = BigDecimal.ONE.subtract(b2bDiscountRate.divide(BigDecimal.valueOf(100)));
                unitPrice = unitPrice.multiply(multiplier).setScale(2, RoundingMode.HALF_UP);
            }

            BigDecimal subtotal = unitPrice.multiply(BigDecimal.valueOf(itemDto.quantity()));
            totalAmount = totalAmount.add(subtotal);

            OrderItem orderItem = OrderItem.builder()
                    .order(order)
                    .product(product)
                    .unitPrice(unitPrice)
                    .quantity(itemDto.quantity())
                    .subtotal(subtotal)
                    // Le taux est recopie, pas reference : la facture doit rester celle du
                    // jour de la vente, meme si le taux du produit est corrige ensuite.
                    .vatRate(serviceTva.tauxDe(product))
                    .build();
            
            order.getItems().add(orderItem);
        }

        // Code promo (facultatif) : validé et appliqué sur le total déjà calculé (qui reflète
        // déjà les prix promotionnels produit et la remise B2B), avant toute création de session
        // de paiement. L'usage n'est consommé qu'une fois la commande effectivement enregistrée
        // ci-dessous, jamais avant, pour ne pas décompter un usage sur une tentative avortée.
        PromoCodeService.DiscountResult discountResult = null;
        if (promoCodeInput != null && !promoCodeInput.isBlank() && !isQuote) {
            discountResult = promoCodeService.validateAndComputeDiscount(promoCodeInput, totalAmount);
            totalAmount = totalAmount.subtract(discountResult.discountAmount());
            order.setDiscountAmount(discountResult.discountAmount());
        }

        // Frais de port : apres les remises, avant le paiement. Le total envoye a Stripe doit
        // etre celui que le client voit, frais compris — sinon il est debite d'un montant
        // qu'il n'a jamais valide.
        if (livraison != null && livraison.shippingCountry() != null
                && !livraison.shippingCountry().isBlank()) {
            var frais = serviceLivraison.calculer(livraison.shippingCountry(), totalAmount);
            order.setShippingRecipient(livraison.shippingRecipient());
            order.setShippingLine1(livraison.shippingLine1());
            order.setShippingLine2(livraison.shippingLine2());
            order.setShippingPostalCode(livraison.shippingPostalCode());
            order.setShippingCity(livraison.shippingCity());
            order.setShippingCountry(livraison.shippingCountry().trim().toUpperCase());
            order.setShippingZone(frais.zone());
            order.setShippingCost(frais.montant());
            totalAmount = totalAmount.add(frais.montant());
        }

        order.setTotalAmount(totalAmount);

        // La devise choisie par le client. Le total reste en euros — devise de référence, dans
        // laquelle la comptabilité est tenue — et le montant réellement facturé en découle par
        // conversion. C'est le SERVEUR qui convertit : l'affichage du navigateur est une
        // commodité, et un montant à encaisser ne se laisse pas dicter par le client.
        Montant montantReference = Montant.euros(totalAmount);
        Devise deviseChoisie = (livraison == null || livraison.devise() == null
                || livraison.devise().isBlank())
                ? Devise.REFERENCE : Devise.de(livraison.devise());
        Montant montantAFacturer = serviceDevises.convertir(montantReference, deviseChoisie);
        order.setPaymentCurrency(deviseChoisie.code());
        order.setPaymentAmount(montantAFacturer.valeur());

        // L'ID de la commande n'existe qu'après un premier save() (GenerationType.UUID
        // n'assigne l'ID qu'à la persistance) — nécessaire pour la session Stripe ci-dessous.
        Order savedOrder = orderRepository.save(order);

        if (discountResult != null) {
            savedOrder.setPromoCode(discountResult.promoCode());
            savedOrder = orderRepository.save(savedOrder);
            promoCodeService.consumeUsage(discountResult.promoCode().getId());
        }

        String clientSecret = null;
        if (paymentMethod == PaymentMethod.STRIPE_CARD) {
            try {
                // Customer Stripe réutilisable : permet à l'utilisateur de retrouver ses cartes
                // enregistrées lors d'un achat précédent (Stripe Elements, cf. doc "Save customer
                // payment methods"). Créé une seule fois puis persisté sur le compte.
                String customerId = user.getStripeCustomerId();
                if (customerId == null) {
                    customerId = stripePaymentService.createCustomer(user.getEmail());
                    user.setStripeCustomerId(customerId);
                    userRepository.save(user);
                }

                // ui_mode "elements" : le formulaire de paiement s'affiche intégré dans notre
                // propre page de checkout (pas de redirection vers checkout.stripe.com). Le
                // navigateur n'est renvoyé vers returnUrl qu'après confirmation du paiement.
                String returnUrl = frontendBaseUrl + "/checkout/complete?session_id={CHECKOUT_SESSION_ID}";

                Session session = stripePaymentService.createElementsCheckoutSessionForCustomer(
                        savedOrder.getId(), montantAFacturer, customerId, returnUrl,
                        "Commande Optimi Santé #" + savedOrder.getOrderNumber(), null
                );
                savedOrder.setStripeCheckoutSessionId(session.getId());
                savedOrder.setStripePaymentIntentId(session.getPaymentIntent());
                clientSecret = session.getClientSecret();
                savedOrder = orderRepository.save(savedOrder);
            } catch (Exception e) {
                log.error("Failed to create Stripe Checkout Session", e);
                throw new RuntimeException("Failed to initialize payment process");
            }
        }

        // --- SPRINT 4: Génération PDF (Devis) ---
        if (isQuote || paymentMethod == PaymentMethod.QUOTE_REQUEST) {
            try {
                java.util.Map<String, Object> quoteData = new java.util.HashMap<>();
                quoteData.put("documentTitle", "DEVIS");
                quoteData.put("orderNumber", savedOrder.getOrderNumber());
                quoteData.put("currentDate", java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")));
                quoteData.put("customerName", user.getEmail());
                quoteData.put("totalAmount", totalAmount.toString());
                
                // Préparation des items
                List<java.util.Map<String, Object>> itemsList = savedOrder.getItems().stream().map(i -> {
                    java.util.Map<String, Object> map = new java.util.HashMap<>();
                    map.put("name", libelleLigne(i));
                    map.put("quantity", i.getQuantity());
                    map.put("unitPrice", i.getUnitPrice().toString());
                    map.put("subtotal", i.getSubtotal().toString());
                    return map;
                }).collect(Collectors.toList());
                quoteData.put("items", itemsList);

                // Le devis annoncait « Total HT » suivi d'une « TVA (0 % — Export/Exonere) »
                // ecrite en dur, avec le meme montant partout : un devis a un etablissement
                // francais y declarait donc une taxe nulle qui ne lui est pas applicable. Les
                // prix etant TTC, la taxe est extraite et ventilee par taux.
                var ventilation = serviceTva.ventiler(savedOrder.getItems().stream()
                        .map(i -> new com.optimisante.backend.domain.catalog.service.ServiceTva
                                .LigneTaxable(i.getSubtotal(), i.getVatRate()))
                        .toList());
                quoteData.put("aTva", serviceTva.estPubliable() && !ventilation.isEmpty());
                quoteData.put("ventilationTva", ventilation.stream().map(x -> java.util.Map.of(
                        "taux", x.taux().stripTrailingZeros().toPlainString(),
                        "ht", x.ht().toPlainString(),
                        "taxe", x.taxe().toPlainString())).toList());
                quoteData.put("totalHt", ventilation.stream()
                        .map(com.optimisante.backend.domain.catalog.service.ServiceTva.Ventilation::ht)
                        .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add).toPlainString());
                quoteData.put("totalTva", ventilation.stream()
                        .map(com.optimisante.backend.domain.catalog.service.ServiceTva.Ventilation::taxe)
                        .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add).toPlainString());

                String pdfUrl = pdfGeneratorService.generateAndUploadPdf("devis-b2b", quoteData, DossierStockage.DOCUMENTS_DEVIS, "QUOTE-" + savedOrder.getOrderNumber());
                savedOrder.setDocumentS3Key(pdfUrl);
                savedOrder = orderRepository.save(savedOrder);
                log.info("Devis PDF généré avec succès pour la commande: {}", savedOrder.getOrderNumber());
            } catch (Exception e) {
                log.error("Erreur lors de la génération du devis PDF", e);
                // On ne bloque pas la création de la commande si le PDF échoue, l'admin pourra le relancer
            }
        }

        OrderResponseDto dto = mapToResponseDto(savedOrder);
        return new OrderResponseDto(
                dto.id(), dto.orderNumber(), dto.paymentMethod(), dto.paymentStatus(),
                dto.status(), dto.isQuote(), dto.totalAmount(), dto.stripePaymentIntentId(),
                dto.stripeCheckoutSessionId(), null, clientSecret, dto.documentS3Key(),
                dto.promoCode(), dto.discountAmount(), dto.quoteDiscountRate(), dto.createdAt(),
                dto.paymentCurrency(), dto.paymentAmount(), dto.refundedAmount(), dto.items()
        );
    }

    /**
     * Utilisé par la page de retour du checkout embarqué (Stripe Elements) : le navigateur ne
     * connaît que l'ID de la Checkout Session Stripe, pas directement l'ID de la commande.
     * Le paiement effectif reste confirmé côté serveur par le webhook (source de vérité) — cet
     * appel ne sert qu'à afficher un état à l'utilisateur juste après la confirmation.
     */
    @Transactional(readOnly = true)
    public java.util.Map<String, Object> getCheckoutSessionStatus(String stripeSessionId) {
        try {
            Session session = stripePaymentService.retrieveSession(stripeSessionId);
            java.util.Map<String, Object> result = new java.util.HashMap<>();
            result.put("status", session.getStatus());
            result.put("paymentStatus", session.getPaymentStatus());
            if (session.getClientReferenceId() != null) {
                orderRepository.findById(UUID.fromString(session.getClientReferenceId()))
                        .ifPresent(order -> result.put("orderNumber", order.getOrderNumber()));
            }
            return result;
        } catch (Exception e) {
            log.error("Failed to retrieve Stripe Checkout Session {}", stripeSessionId, e);
            throw new RuntimeException("Impossible de récupérer le statut du paiement");
        }
    }

    @Transactional(readOnly = true)
    public Page<OrderResponseDto> getMyOrders(Pageable pageable) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new IllegalStateException("User must be authenticated");
        }
        UUID userId = UUID.fromString(auth.getPrincipal().toString());
        
        return orderRepository.findByUserId(userId, pageable)
                .map(this::mapToResponseDto);
    }

    /**
     * Constate un remboursement émis depuis Stripe sur une commande de la boutique.
     *
     * <p><b>Le montant rendu se cumule sur la commande, le statut dit si elle est honorée.</b>
     * Les mêler obligerait à choisir entre les deux : un remboursement partiel de dix euros ne
     * fait pas d'une commande une commande non honorée, mais il doit sortir dix euros du
     * chiffre d'affaires. Les agrégats financiers déduisent donc {@code refunded_amount}, et le
     * statut ne passe à {@code REFUNDED} qu'au remboursement intégral.</p>
     *
     * <p><b>Naturellement idempotent.</b> Stripe transmet le cumul remboursé, pas le dernier
     * versement : rejouer un événement réécrit la même valeur.</p>
     *
     * <p><b>Le stock n'est pas réapprovisionné, et c'est la règle voulue.</b> Le stock ne bouge
     * qu'à la confirmation du paiement : un paiement qui échoue ne l'a donc jamais entamé, et
     * les réservations prises au panier expirent d'elles-mêmes. Un remboursement, lui, porte sur
     * une commande dont le paiement a réussi — la marchandise est partie. Le retour physique se
     * constate à la réception, par l'administration, pas par un webhook.</p>
     *
     * @return {@code true} si une commande correspond à ce paiement
     */
    @Transactional
    public boolean constaterRemboursement(
            com.optimisante.backend.domain.orders.webhook.RemboursementConstate remboursement) {

        String paymentIntentId = remboursement.paymentIntentId();
        if (paymentIntentId == null || paymentIntentId.isBlank()) {
            log.warn("Remboursement Stripe sans identifiant de paiement : ignoré.");
            return false;
        }

        Order order = orderRepository.findByStripePaymentIntentId(paymentIntentId).orElse(null);
        if (order == null) {
            return false;
        }

        BigDecimal rendu = remboursement.montantRembourse().valeur();
        // Garde-fou : la contrainte de base refuse un montant rendu superieur au total, et une
        // erreur de contrainte dans un webhook ferait rejouer Stripe sans fin.
        if (rendu.compareTo(order.getTotalAmount()) > 0) {
            log.error("Remboursement de {} supérieur au total de la commande {} ({}) : "
                    + "plafonné au total.", rendu, order.getOrderNumber(), order.getTotalAmount());
            rendu = order.getTotalAmount();
        }

        order.setRefundedAmount(rendu);

        if (remboursement.integral()) {
            if (order.getPaymentStatus() == PaymentStatus.REFUNDED) {
                log.info("Commande {} déjà remboursée. Idempotence respectée.",
                        order.getOrderNumber());
                return true;
            }
            PaymentStatus precedent = order.getPaymentStatus();
            order.setPaymentStatus(PaymentStatus.REFUNDED);
            log.info("Commande {} remboursée intégralement ({}) : statut {} → REFUNDED.",
                    order.getOrderNumber(), remboursement.montantRembourse(), precedent);
        } else {
            log.info("Commande {} remboursée partiellement : {} sur {}. "
                    + "Le statut reste {}, et le chiffre d'affaires est corrigé d'autant.",
                    order.getOrderNumber(), remboursement.montantRembourse(),
                    order.getTotalAmount(), order.getPaymentStatus());
        }

        orderRepository.save(order);
        return true;
    }

    /**
     * Confirme le paiement d'une commande, en retenant au passage son identifiant chez Stripe.
     *
     * <p><b>Pourquoi cet identifiant se capture ici et non à la création de la session.</b> Il
     * l'était, et il arrivait toujours nul : une session de paiement n'a pas encore de
     * {@code PaymentIntent} au moment où on la crée — Stripe ne l'attribue qu'une fois le
     * client engagé dans le règlement. Onze commandes réglées par carte portaient donc leur
     * identifiant de session, et aucune son identifiant de paiement.</p>
     *
     * <p>Ce n'était sans conséquence que tant que personne ne cherchait une commande par ce
     * champ. Le webhook de remboursement le fait : c'est le seul lien que l'événement {@code
     * charge.refunded} porte vers nos données. Sans cette capture, aucun remboursement n'aurait
     * jamais trouvé sa commande.</p>
     *
     * @param paymentIntentId identifiant du paiement chez Stripe, tel que la session confirmée
     *                        le porte ; {@code null} pour une confirmation manuelle
     */
    @Transactional
    public void confirmOrderPayment(UUID orderId, String paymentIntentId) {
        if (paymentIntentId != null && !paymentIntentId.isBlank()) {
            orderRepository.findById(orderId).ifPresent(commande -> {
                if (commande.getStripePaymentIntentId() == null) {
                    commande.setStripePaymentIntentId(paymentIntentId);
                    orderRepository.save(commande);
                }
            });
        }
        confirmOrderPayment(orderId);
    }

    /**
     * Le règlement dans sa devise, quand elle n'est pas l'euro.
     *
     * <p>{@code null} pour une commande réglée en euros : le reçu reste alors exactement celui
     * qu'il était, sans mention surnuméraire.</p>
     */
    private Encaissement.ReglementEnDevise reglementEnDevise(Order order) {
        String devise = order.getPaymentCurrency();
        if (devise == null || Devise.REFERENCE.code().equals(devise)
                || order.getPaymentAmount() == null
                || order.getTotalAmount().signum() == 0) {
            return null;
        }
        // Le taux est recalculé depuis les deux montants figés sur la commande, et non relu de
        // la grille : celle-ci a pu changer depuis, et un reçu doit dire ce qui s'est passé.
        BigDecimal taux = order.getPaymentAmount()
                .divide(order.getTotalAmount(), 6, java.math.RoundingMode.HALF_UP);
        return new Encaissement.ReglementEnDevise(devise, order.getPaymentAmount(), taux);
    }

    @Transactional
    public void confirmOrderPayment(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        if (order.getPaymentStatus() == PaymentStatus.PAID) {
            log.info("Order {} is already PAID. Idempotency triggered.", orderId);
            return;
        }

        order.setPaymentStatus(PaymentStatus.PAID);
        orderRepository.save(order);

        // Deduct actual stock and remove reservations
        for (OrderItem item : order.getItems()) {
            Product product = productRepository.findByIdWithPessimisticLock(item.getProduct().getId())
                    .orElseThrow(() -> new RuntimeException("Product not found"));

            product.setStockQuantity(product.getStockQuantity() - item.getQuantity());
            productRepository.save(product);

            List<StockReservation> reservations = stockReservationRepository
                    .findByUserIdAndProductId(order.getUser().getId(), product.getId());
            
            // Just delete the reservations related to this product and user (we assume FIFO or all for this checkout session)
            stockReservationRepository.deleteAll(reservations);
        }

        // --- Reçu de paiement ---
        // Le bloc précédent assemblait une Map à la main : aucune de ses clés ne correspondait
        // au gabarit, et six champs du reçu restaient blancs. Il recalculait aussi le total en
        // additionnant les lignes, ce qui ignorait la remise — un reçu annonçait 63 144,43 €
        // pour une commande réglée 56 829,99 €.
        receiptIssuer.emettre(new Encaissement(
                // Référence du paiement chez Stripe si elle existe, identifiant de commande
                // sinon : il en faut une, c'est elle qui rend l'émission idempotente.
                order.getStripePaymentIntentId() != null
                        ? order.getStripePaymentIntentId()
                        : "order:" + order.getId(),
                MotifPaiement.COMMANDE,
                order.getTotalAmount(),          // ce qui a été réglé, lu et non recalculé
                order.getDiscountAmount(),
                order.getPaymentMethod() == null ? "Carte bancaire"
                        : order.getPaymentMethod().libelle(),
                order.getCreatedAt(),
                order.getItems().stream()
                        // Le taux fige sur la ligne accompagne le recu : c'est lui qui
                        // permet d'y ventiler la taxe, et non le taux actuel du produit.
                        .map(i -> Encaissement.LigneEncaissement.vente(
                                libelleLigne(i), i.getQuantity(), i.getUnitPrice(),
                                i.getSubtotal(), i.getVatRate()))
                        .toList(),
                order.getUser() == null ? null : order.getUser().getId(),
                null,
                order.getId(),
                // Pas de zone : la commande n'a pas d'adresse de destination — une demande de
                // devis, ou une commande anterieure a la grille de livraison. Le recu reste
                // alors celui d'avant, sans pied de transport.
                order.getShippingZone() == null ? null : new Encaissement.Livraison(
                        order.getShippingCost(),
                        order.getShippingZone()
                                != com.optimisante.backend.domain.orders.shipping.ZoneLivraison.FRANCE),
                reglementEnDevise(order)
        )).ifPresent(recu -> {
            // La commande continue de porter la clé du document : c'est ce que lit « Mes
            // commandes » pour proposer « Ouvrir le PDF ».
            if (recu.getDocumentKey() != null) {
                order.setDocumentS3Key(recu.getDocumentKey());
                orderRepository.save(order);
            }
        });

        log.info("Order {} payment confirmed. Stock deducted.", orderId);

        // Notification in-app + e-mail au client (traité après commit, hors chemin critique).
        eventPublisher.publishEvent(new com.optimisante.backend.domain.notification.event.NotificationEvents.OrderPaid(
                order.getId(), order.getUser().getId(), order.getUser().getEmail(),
                order.getOrderNumber(), order.getTotalAmount()));
    }

    /**
     * Toutes les commandes de la plateforme, tous statuts confondus — nécessaire à l'admin pour
     * suivre les commandes hors devis (carte, virement), absent jusqu'ici : seules les commandes
     * marquées "devis" étaient consultables via getAllQuotes, laissant les commandes carte/
     * virement invisibles côté admin (BANK_TRANSFER en particulier, qui ne passe jamais par le
     * webhook Stripe et restait donc indéfiniment UNPAID sans qu'aucune action ne soit possible).
     */
    @Transactional(readOnly = true)
    public Page<OrderResponseDto> getAllOrders(Pageable pageable) {
        return orderRepository.findAll(pageable).map(this::mapToResponseDto);
    }

    @Transactional(readOnly = true)
    public Page<OrderResponseDto> getAllQuotes(Pageable pageable) {
        return orderRepository.findByIsQuoteTrueOrderByCreatedAtDesc(pageable)
                .map(this::mapToResponseDto);
    }

    /** Une commande vue par l'administration, construite comme partout ailleurs. */
    @Transactional(readOnly = true)
    public OrderResponseDto getOrderForAdmin(UUID orderId) {
        return mapToResponseDto(orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Commande introuvable.")));
    }

    @Transactional
    public OrderResponseDto updateOrderStatus(UUID orderId, OrderStatus newStatus) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));
        
        order.setStatus(newStatus);
        Order savedOrder = orderRepository.save(order);
        return mapToResponseDto(savedOrder);
    }

    @Transactional
    public void updateOrderDocumentKey(UUID orderId, String documentS3Key) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));
        order.setDocumentS3Key(documentS3Key);
        orderRepository.save(order);
    }

    private String generateOrderNumber() {
        String datePart = OffsetDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String randomPart = UUID.randomUUID().toString().substring(0, 4).toUpperCase();
        return "OPT-" + datePart + "-" + randomPart;
    }


    /**
     * Libellé d'une ligne de commande, résistant à la disparition du produit.
     *
     * <p>{@code Product} porte {@code @SQLRestriction("deleted_at IS NULL AND is_active = true")} :
     * dès qu'un produit est désactivé ou supprimé, l'association n'est plus résolvable et
     * {@code getProduct().getName()} leve une {@code EntityNotFoundException}. Vérifié : désactiver
     * un seul produit déjà commandé faisait échouer <b>toute</b> la liste des commandes de
     * l'administration (HTTP 400, « No row with the given identifier exists for entity Product »).
     * Une commande est une pièce comptable : elle doit rester lisible même si l'article a quitté
     * le catalogue.</p>
     *
     * <p>Correctif minimal. Le correctif de fond consiste à figer le libellé dans
     * {@code order_items} au moment de la commande, comme {@code unit_price} l'est déjà — un
     * produit renommé après coup fait aujourd'hui mentir l'historique. Traité comme un sujet
     * distinct pour ne pas toucher au parcours de commande.</p>
     */
    private static String libelleLigne(OrderItem item) {
        try {
            Product produit = item.getProduct();
            return produit != null ? produit.getName() : "Article retiré du catalogue";
        } catch (jakarta.persistence.EntityNotFoundException e) {
            return "Article retiré du catalogue";
        }
    }

    private static java.util.UUID identifiantLigne(OrderItem item) {
        try {
            Product produit = item.getProduct();
            return produit != null ? produit.getId() : null;
        } catch (jakarta.persistence.EntityNotFoundException e) {
            return null;
        }
    }

    private OrderResponseDto mapToResponseDto(Order order) {
        List<OrderItemDto> itemDtos = order.getItems().stream()
                .map(item -> new OrderItemDto(
                        item.getId(),
                        identifiantLigne(item),
                        libelleLigne(item),
                        item.getUnitPrice(),
                        item.getQuantity(),
                        item.getSubtotal()
                ))
                .collect(Collectors.toList());

        return new OrderResponseDto(
                order.getId(),
                order.getOrderNumber(),
                order.getPaymentMethod(),
                order.getPaymentStatus(),
                order.getStatus(),
                order.getIsQuote(),
                order.getTotalAmount(),
                order.getStripePaymentIntentId(),
                order.getStripeCheckoutSessionId(),
                null, // paymentUrl/clientSecret ne sont renseignés que juste après la création
                null,
                order.getDocumentS3Key(),
                order.getPromoCode() != null ? order.getPromoCode().getCode() : null,
                order.getDiscountAmount(),
                order.getQuoteDiscountRate(),
                order.getCreatedAt(),
                order.getPaymentCurrency(),
                order.getPaymentAmount(),
                order.getRefundedAmount(),
                itemDtos
        );
    }
}

package com.optimisante.backend.domain.orders.service;

import com.optimisante.backend.config.tenant.TenantContext;
import com.optimisante.backend.domain.catalog.entity.Product;
import com.optimisante.backend.domain.catalog.repository.ProductRepository;
import com.optimisante.backend.domain.catalog.service.StockReservationService;
import com.optimisante.backend.domain.catalog.repository.StockReservationRepository;
import com.optimisante.backend.domain.catalog.entity.StockReservation;
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
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final CompanyProfileRepository companyProfileRepository;
    private final StockReservationService stockReservationService;
    private final StockReservationRepository stockReservationRepository;
    private final StripePaymentService stripePaymentService;
    private final PdfGeneratorService pdfGeneratorService;
    private final PromoCodeService promoCodeService;

    @org.springframework.beans.factory.annotation.Value("${app.mail.frontend-base-url}")
    private String frontendBaseUrl;

    @Transactional
    public OrderResponseDto createCheckoutOrder(CheckoutRequestDto request) {
        return processOrderCreation(request.items(), request.paymentMethod(), false, null, request.promoCode());
    }

    @Transactional
    public OrderResponseDto createQuoteRequest(QuoteRequestDto request) {
        // Les codes promo ne s'appliquent pas aux devis B2B (négociation individuelle ensuite).
        return processOrderCreation(request.items(), PaymentMethod.QUOTE_REQUEST, true, request.notes(), null);
    }

    private OrderResponseDto processOrderCreation(List<CheckoutItemDto> items, PaymentMethod paymentMethod, boolean isQuote, String notes, String promoCodeInput) {
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

        boolean isB2B = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_" + Role.CLIENT_B2B.name()));
        
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

        order.setTotalAmount(totalAmount);

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
                        savedOrder.getId(), totalAmount, customerId, returnUrl,
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
                    map.put("name", i.getProduct().getName());
                    map.put("quantity", i.getQuantity());
                    map.put("unitPrice", i.getUnitPrice().toString());
                    map.put("subtotal", i.getSubtotal().toString());
                    return map;
                }).collect(Collectors.toList());
                quoteData.put("items", itemsList);

                String pdfUrl = pdfGeneratorService.generateAndUploadPdf("devis-b2b", quoteData, "docs/quotes", "QUOTE-" + savedOrder.getOrderNumber());
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
                dto.promoCode(), dto.discountAmount(), dto.createdAt(), dto.items()
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

        // --- SPRINT 4: Génération PDF (Reçu) ---
        try {
            java.util.Map<String, Object> receiptData = new java.util.HashMap<>();
            receiptData.put("documentTitle", "REÇU DE PAIEMENT");
            receiptData.put("orderNumber", order.getOrderNumber());
            receiptData.put("currentDate", java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")));
            receiptData.put("customerName", order.getUser().getEmail());
            
            BigDecimal totalAmount = order.getItems().stream().map(OrderItem::getSubtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
            receiptData.put("totalAmount", totalAmount.toString());
            
            List<java.util.Map<String, Object>> itemsList = order.getItems().stream().map(i -> {
                java.util.Map<String, Object> map = new java.util.HashMap<>();
                map.put("name", i.getProduct().getName());
                map.put("quantity", i.getQuantity());
                map.put("unitPrice", i.getUnitPrice().toString());
                map.put("subtotal", i.getSubtotal().toString());
                return map;
            }).collect(Collectors.toList());
            receiptData.put("items", itemsList);

            String pdfUrl = pdfGeneratorService.generateAndUploadPdf("recu-paiement", receiptData, "docs/receipts", "RECEIPT-" + order.getOrderNumber());
            order.setDocumentS3Key(pdfUrl);
            orderRepository.save(order);
            log.info("Reçu PDF généré avec succès pour la commande: {}", order.getOrderNumber());
        } catch (Exception e) {
            log.error("Erreur lors de la génération du reçu PDF", e);
        }

        log.info("Order {} payment confirmed. Stock deducted.", orderId);
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

    private OrderResponseDto mapToResponseDto(Order order) {
        List<OrderItemDto> itemDtos = order.getItems().stream()
                .map(item -> new OrderItemDto(
                        item.getId(),
                        item.getProduct().getId(),
                        item.getProduct().getName(),
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
                order.getCreatedAt(),
                itemDtos
        );
    }
}

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

    @Transactional
    public OrderResponseDto createCheckoutOrder(CheckoutRequestDto request) {
        return processOrderCreation(request.items(), request.paymentMethod(), false, null);
    }

    @Transactional
    public OrderResponseDto createQuoteRequest(QuoteRequestDto request) {
        return processOrderCreation(request.items(), PaymentMethod.QUOTE_REQUEST, true, request.notes());
    }

    private OrderResponseDto processOrderCreation(List<CheckoutItemDto> items, PaymentMethod paymentMethod, boolean isQuote, String notes) {
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

        Order order = Order.builder()
                .tenant(tenant)
                .user(user)
                .orderNumber(generateOrderNumber())
                .paymentMethod(paymentMethod)
                .paymentStatus(isQuote ? PaymentStatus.QUOTE_SENT : PaymentStatus.UNPAID)
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

            BigDecimal unitPrice = product.getBasePrice();
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

        String paymentUrl = null;
        if (paymentMethod == PaymentMethod.STRIPE_CARD) {
            try {
                // In a real app, you would pass dynamic success/cancel URLs from frontend
                String successUrl = "http://localhost:5173/checkout/success";
                String cancelUrl = "http://localhost:5173/checkout/cancel";
                
                Session session = stripePaymentService.createCheckoutSession(
                        order.getId(), totalAmount, user.getEmail(), successUrl, cancelUrl
                );
                order.setStripeCheckoutSessionId(session.getId());
                order.setStripePaymentIntentId(session.getPaymentIntent());
                paymentUrl = session.getUrl();
            } catch (Exception e) {
                log.error("Failed to create Stripe Checkout Session", e);
                throw new RuntimeException("Failed to initialize payment process");
            }
        }

        Order savedOrder = orderRepository.save(order);
        OrderResponseDto dto = mapToResponseDto(savedOrder);
        return new OrderResponseDto(
                dto.id(), dto.orderNumber(), dto.paymentMethod(), dto.paymentStatus(),
                dto.status(), dto.isQuote(), dto.totalAmount(), dto.stripePaymentIntentId(),
                dto.stripeCheckoutSessionId(), paymentUrl, dto.createdAt(), dto.items()
        );
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

        log.info("Order {} payment confirmed. Stock deducted.", orderId);
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
                null, // paymentUrl is only set during creation
                order.getCreatedAt(),
                itemDtos
        );
    }
}

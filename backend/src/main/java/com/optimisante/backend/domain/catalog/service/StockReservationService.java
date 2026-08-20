package com.optimisante.backend.domain.catalog.service;

import com.optimisante.backend.domain.catalog.entity.Product;
import com.optimisante.backend.domain.catalog.entity.StockReservation;
import com.optimisante.backend.domain.catalog.exception.InsufficientStockException;
import com.optimisante.backend.domain.catalog.repository.ProductRepository;
import com.optimisante.backend.domain.catalog.repository.StockReservationRepository;
import com.optimisante.backend.domain.identity.entity.User;
import com.optimisante.backend.domain.identity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockReservationService {

    private final ProductRepository productRepository;
    private final StockReservationRepository stockReservationRepository;
    private final UserRepository userRepository;

    @Transactional
    public StockReservation reserveStock(UUID productId, UUID userId, int quantity, int ttlMinutes) {
        // 1. Fetch Product with Pessimistic Write Lock
        // This guarantees that no other transaction can read or update this row until we commit.
        Product product = productRepository.findByIdWithPessimisticLock(productId)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        if (!product.getIsActive() || product.getDeletedAt() != null) {
            throw new IllegalStateException("Product is inactive or deleted");
        }

        // 2. Calculate Active Reservations for this product
        Integer activeReservationsSum = stockReservationRepository.sumActiveReservationsByProductId(productId);

        // 3. Calculate Available Stock
        int availableStock = product.getStockQuantity() - activeReservationsSum;

        if (availableStock < quantity) {
            log.warn("Insufficient stock for product {}. Requested: {}, Available: {}", productId, quantity, availableStock);
            throw new InsufficientStockException("Insufficient stock. Available: " + availableStock);
        }

        // 4. Fetch User
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // 5. Create and Save Reservation
        StockReservation reservation = StockReservation.builder()
                .product(product)
                .user(user)
                .quantity(quantity)
                .expiresAt(OffsetDateTime.now().plusMinutes(ttlMinutes))
                .build();

        return stockReservationRepository.save(reservation);
    }

    @Transactional
    public void releaseStock(UUID reservationId) {
        stockReservationRepository.findById(reservationId).ifPresent(reservation -> {
            stockReservationRepository.delete(reservation);
            log.info("Released stock reservation: {}", reservationId);
        });
    }

    // Runs every minute to delete expired reservations
    @Scheduled(cron = "0 * * * * *")
    @Transactional
    public void cleanExpiredReservations() {
        OffsetDateTime now = OffsetDateTime.now();
        log.debug("Cleaning expired stock reservations before {}", now);
        stockReservationRepository.deleteByExpiresAtBefore(now);
    }
}

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

    /**
     * Réserve du stock, <b>si tant est qu'il y ait un stock à réserver</b>.
     *
     * <p>Une réservation pose un verrou pessimiste sur la ligne produit et retient une
     * quantité pendant trente minutes — vingt-quatre heures sur un devis. Sur un produit dont
     * personne ne compte le stock, ou que le fournisseur expédie depuis son propre entrepôt,
     * elle ne protège rien : elle met des acheteurs en file derrière un chiffre inventé, et
     * refuse une commande de gros au motif d'un plafond qui n'existe pas.</p>
     *
     * <p>Hors {@code PROPRE}, cette méthode ne fait donc rien et renvoie {@code null}. Elle
     * ne lève pas : l'appelant n'a aucune décision à prendre, la vente suit son cours.</p>
     */
    @Transactional
    public StockReservation reserveStock(UUID productId, UUID userId, int quantity, int ttlMinutes) {
        // Lecture sans verrou, avant de poser le verrou : le mode ne change qu'à la main
        // depuis l'administration, et le lire sous verrou ferait attendre chaque passage de
        // commande pour une valeur qui ne bouge pas au fil des ventes.
        boolean stockTenuParNous = productRepository.findById(productId)
                .map(p -> p.getModeStock() != null && p.getModeStock().estOpposable())
                .orElse(false);
        if (!stockTenuParNous) {
            return null;
        }

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

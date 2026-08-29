package com.optimisante.backend.domain.orders.controller;

import com.optimisante.backend.common.storage.StorageService;
import com.optimisante.backend.domain.document.service.PdfGeneratorService;
import com.optimisante.backend.domain.orders.dto.OrderResponseDto;
import com.optimisante.backend.domain.orders.entity.OrderStatus;
import com.optimisante.backend.domain.orders.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/admin/orders")
@RequiredArgsConstructor
public class AdminOrderResource {

    private final OrderService orderService;
    private final PdfGeneratorService pdfGeneratorService;
    private final StorageService storageService;

    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN')")
    public ResponseEntity<Page<OrderResponseDto>> getAllOrders(@PageableDefault(size = 20, sort = "createdAt", direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(orderService.getAllOrders(pageable));
    }

    @GetMapping("/quotes")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN')")
    public ResponseEntity<Page<OrderResponseDto>> getAllQuotes(@PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(orderService.getAllQuotes(pageable));
    }

    /**
     * Confirmation manuelle d'un paiement (virement bancaire reçu, ou tout cas où le webhook
     * Stripe n'aurait pas abouti) — réutilise exactement la même logique que la confirmation
     * automatique via webhook (déduction de stock, génération du reçu), idempotente.
     */
    @PatchMapping("/{id}/confirm-payment")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN')")
    public ResponseEntity<Void> confirmPayment(@PathVariable UUID id) {
        orderService.confirmOrderPayment(id);
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN')")
    public ResponseEntity<OrderResponseDto> updateOrderStatus(
            @PathVariable UUID id,
            @RequestParam("status") OrderStatus status) {
        
        OrderResponseDto updatedOrder = orderService.updateOrderStatus(id, status);

        // Si le devis est validé, on génère le PDF définitif et on l'upload
        if (status == OrderStatus.VALIDATED) {
            try {
                // Création du payload pour le PDF
                Map<String, Object> pdfData = Map.of(
                        "clientName", "Client B2B", // Dans une implémentation complète, on récupérerait le nom depuis l'entité User/CompanyProfile
                        "clientAddress", "N/A",
                        "clientVat", "N/A",
                        "orderReference", updatedOrder.orderNumber(),
                        "date", java.time.LocalDate.now().toString(),
                        "items", updatedOrder.items().stream().map(item -> Map.of(
                                "description", item.productName(),
                                "quantity", item.quantity(),
                                "unitPrice", item.unitPrice(),
                                "subtotal", item.subtotal()
                        )).toList(),
                        "totalAmount", updatedOrder.totalAmount()
                );

                // 1. Génération du PDF
                byte[] pdfBytes = pdfGeneratorService.generateQuotePdf(pdfData);

                // 2. Upload vers Cloudinary
                String fileName = updatedOrder.orderNumber() + "-DEVIS";
                String publicId = storageService.uploadGeneratedPdf(pdfBytes, "docs/devis", fileName);

                // 3. Mise à jour de la commande avec la clé S3/Cloudinary
                orderService.updateOrderDocumentKey(id, publicId);
                
                // Mettre à jour l'objet retourné avec la nouvelle clé
                updatedOrder = new OrderResponseDto(
                        updatedOrder.id(), updatedOrder.orderNumber(), updatedOrder.paymentMethod(), updatedOrder.paymentStatus(),
                        updatedOrder.status(), updatedOrder.isQuote(), updatedOrder.totalAmount(), updatedOrder.stripePaymentIntentId(),
                        updatedOrder.stripeCheckoutSessionId(), updatedOrder.paymentUrl(), updatedOrder.clientSecret(), publicId,
                        updatedOrder.promoCode(), updatedOrder.discountAmount(), updatedOrder.createdAt(), updatedOrder.items()
                );

                log.info("PDF generated and uploaded for order {}: publicId={}", id, publicId);
            } catch (Exception e) {
                log.error("Erreur lors de la génération ou de l'upload du devis PDF pour la commande " + id, e);
                // On ne bloque pas la réponse HTTP, le statut a bien été changé en base
            }
        }

        return ResponseEntity.ok(updatedOrder);
    }
}

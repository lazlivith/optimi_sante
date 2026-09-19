package com.optimisante.backend.domain.orders.service;

import com.optimisante.backend.common.storage.DossierStockage;
import com.optimisante.backend.common.storage.StorageService;
import com.optimisante.backend.domain.catalog.repository.ProductRepository;
import com.optimisante.backend.domain.document.service.PdfGeneratorService;
import com.optimisante.backend.domain.orders.entity.Order;
import com.optimisante.backend.domain.orders.entity.OrderItem;
import com.optimisante.backend.domain.orders.entity.PaymentStatus;
import com.optimisante.backend.domain.orders.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Remise accordée sur un devis, et réémission du document.
 *
 * <p><b>Pourquoi elle existe.</b> Un devis de gros volume se négocie : l'administration consent un
 * tarif dégressif avant de le valider. Jusqu'ici, elle ne pouvait que valider ou refuser le montant
 * calculé au panier, et ajuster un prix imposait de tout refaire hors plateforme.</p>
 *
 * <p><b>Le PDF suit, toujours.</b> Le devis remisé est régénéré sous la même référence : sans cela,
 * le client conserverait un document qui ne correspond plus au prix accordé — et c'est ce document
 * qui fait foi.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RemiseDevisService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final PdfGeneratorService pdfGeneratorService;
    private final StorageService storageService;

    /** Stock disponible d'une ligne, pour que l'administration sache ce qu'elle promet. */
    public record LigneDevis(String designation, int quantite, BigDecimal prixUnitaire, BigDecimal sousTotal,
                             Integer stockDisponible) {
    }

    @Transactional(readOnly = true)
    public List<LigneDevis> lignes(UUID orderId) {
        Order devis = requireDevis(orderId);
        return devis.getItems().stream()
                .map(i -> new LigneDevis(designation(i), i.getQuantity(), i.getUnitPrice(), i.getSubtotal(),
                        stock(i)))
                .toList();
    }

    /**
     * Applique une remise globale au devis et réémet le PDF.
     *
     * @param taux remise en pourcentage, de 0 à 100 ; zéro rétablit le montant d'origine
     */
    @Transactional
    public Order appliquerRemise(UUID orderId, BigDecimal taux, UUID adminId) {
        if (taux == null || taux.signum() < 0 || taux.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("La remise doit être comprise entre 0 et 100 %.");
        }
        Order devis = requireDevis(orderId);
        if (devis.getPaymentStatus() != PaymentStatus.QUOTE_SENT) {
            throw new IllegalStateException("Ce devis n'est plus modifiable (statut : " + devis.getPaymentStatus() + ").");
        }

        // Le total se recalcule depuis les lignes, jamais depuis le total précédent : appliquer 10 %
        // deux fois de suite doit donner 10 %, pas 19 %.
        BigDecimal brut = devis.getItems().stream()
                .map(OrderItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal remise = brut.multiply(taux).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        devis.setQuoteDiscountRate(taux);
        devis.setDiscountAmount(remise);
        devis.setTotalAmount(brut.subtract(remise).setScale(2, RoundingMode.HALF_UP));
        devis.setQuoteAdjustedBy(adminId);
        devis.setQuoteAdjustedAt(OffsetDateTime.now());
        Order enregistre = orderRepository.save(devis);

        reemettreDocument(enregistre, brut, remise);
        log.info("Devis {} remisé de {} % ({} → {})", enregistre.getOrderNumber(), taux, brut, enregistre.getTotalAmount());
        return orderRepository.save(enregistre);
    }

    /** Régénère le devis PDF avec les montants à jour ; un échec ne perd pas la remise enregistrée. */
    private void reemettreDocument(Order devis, BigDecimal brut, BigDecimal remise) {
        try {
            Map<String, Object> donnees = new HashMap<>();
            donnees.put("documentTitle", "DEVIS");
            donnees.put("orderNumber", devis.getOrderNumber());
            donnees.put("currentDate", LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
            donnees.put("customerName", devis.getUser().getEmail());
            donnees.put("totalAmount", devis.getTotalAmount().toString());
            donnees.put("subtotalAmount", brut.toString());
            donnees.put("discountRate", devis.getQuoteDiscountRate());
            donnees.put("discountAmount", remise.toString());
            donnees.put("items", devis.getItems().stream().map(i -> {
                Map<String, Object> ligne = new HashMap<>();
                ligne.put("name", designation(i));
                ligne.put("quantity", i.getQuantity());
                ligne.put("unitPrice", i.getUnitPrice().toString());
                ligne.put("subtotal", i.getSubtotal().toString());
                return ligne;
            }).toList());

            String ancienne = devis.getDocumentS3Key();
            String cle = pdfGeneratorService.generateAndUploadPdf("devis-b2b", donnees,
                    DossierStockage.DOCUMENTS_DEVIS, "QUOTE-" + devis.getOrderNumber());
            devis.setDocumentS3Key(cle);
            retirer(ancienne, cle, devis.getOrderNumber());
        } catch (Exception e) {
            log.error("Devis {} : PDF non réémis après remise ({})", devis.getOrderNumber(), e.getMessage(), e);
        }
    }

    /**
     * Retire la version précédente du devis, une fois la nouvelle déposée.
     *
     * <p>Une négociation peut demander trois ou quatre allers-retours : sans cela, chaque remise
     * laisserait un PDF de plus au stockage, tous périmés sauf le dernier. L'échec de la
     * suppression ne remonte pas — le devis à jour est enregistré, c'est le seul enjeu.</p>
     */
    private void retirer(String ancienne, String nouvelle, String reference) {
        if (ancienne == null || ancienne.isBlank() || ancienne.equals(nouvelle)) {
            return;
        }
        try {
            storageService.deleteDocument(ancienne);
        } catch (Exception e) {
            log.warn("Devis {} : version précédente non retirée du stockage ({})", reference, e.getMessage());
        }
    }

    private Order requireDevis(UUID orderId) {
        Order commande = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Commande introuvable."));
        if (!Boolean.TRUE.equals(commande.getIsQuote())) {
            throw new IllegalStateException("Cette commande n'est pas un devis.");
        }
        return commande;
    }

    /** Même précaution que {@code OrderService.libelleLigne} : un produit supprimé lève à l'accès. */
    private static String designation(OrderItem item) {
        try {
            return item.getProduct() != null ? item.getProduct().getName() : "Article retiré du catalogue";
        } catch (jakarta.persistence.EntityNotFoundException e) {
            return "Article retiré du catalogue";
        }
    }

    /** Stock lu en natif : un produit désactivé depuis la demande doit tout de même se compter. */
    private Integer stock(OrderItem item) {
        try {
            return item.getProduct() == null ? null : productRepository.trouverStock(item.getProduct().getId());
        } catch (jakarta.persistence.EntityNotFoundException e) {
            return null;
        }
    }
}

package com.optimisante.backend.domain.document.recu;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Un reçu émis. Une ligne par encaissement, jamais deux.
 *
 * <p>L'unicité de {@link #reference} est portée par la base, pas par un test applicatif : deux
 * livraisons du même webhook Stripe peuvent arriver en parallèle, et un « existe déjà ? » suivi
 * d'un INSERT laisse passer les deux.</p>
 */
@Entity
@Table(name = "payment_receipts")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PaymentReceipt {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, length = 30, unique = true)
    private String numero;

    /** Identifiant de l'encaissement côté source — la clé de l'idempotence. */
    @Column(nullable = false, length = 255, unique = true)
    private String reference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private MotifPaiement motif;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal montant;

    @Column(nullable = false, length = 3)
    private String devise = "EUR";

    @Column(name = "beneficiaire_user_id")
    private UUID beneficiaireUserId;

    @Column(name = "enrollment_id")
    private UUID enrollmentId;

    @Column(name = "order_id")
    private UUID orderId;

    /** Clé de stockage du PDF. Nulle si le rendu a échoué : le reçu reste alors rejouable. */
    @Column(name = "document_key", length = 255)
    private String documentKey;

    @Column(name = "emis_le", nullable = false)
    private OffsetDateTime emisLe;

    @PrePersist
    void horodater() {
        if (emisLe == null) emisLe = OffsetDateTime.now();
    }
}

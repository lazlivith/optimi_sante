package com.optimisante.backend.domain.finance;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Le taux auquel une devise de présentation se déduit du prix de référence.
 *
 * <p>Il n'y a <b>pas</b> de table de prix par devise : un prix est stocké une fois, en euros, et
 * converti pour être affiché. Deux prix saisis seraient deux vérités à tenir d'accord, et la
 * seconde finirait par dater.</p>
 */
@Entity
@Table(name = "taux_change")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TauxChange {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    /** Code ISO 4217, en majuscules. */
    @Column(nullable = false, length = 3)
    private String devise;

    /** Combien d'unités de cette devise valent un euro. */
    @Column(nullable = false, precision = 18, scale = 6)
    private BigDecimal taux;

    /**
     * Multiple auquel le montant converti remonte.
     *
     * <p>0,01 pour l'euro — aucun arrondi visible. 1 000 pour le franc CFA, où le centime
     * n'existe pas et où un prix doit se lire : 328 000 FCFA, pas 327 978,5.</p>
     */
    @Column(name = "palier_arrondi", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal palierArrondi = new BigDecimal("0.01");

    /** Une devise inactive n'est ni proposée au client, ni acceptée au paiement. */
    @Column(nullable = false)
    @Builder.Default
    private Boolean actif = true;

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private OffsetDateTime updatedAt = OffsetDateTime.now();
}

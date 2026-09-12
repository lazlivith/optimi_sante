package com.optimisante.backend.domain.document.recu;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Un règlement encaissé, décrit assez complètement pour qu'un reçu puisse en être tiré.
 *
 * <p>Chaque parcours de paiement en construit un et le remet à {@code PaymentReceiptIssuer}.
 * C'est un {@code record} et non une {@code Map} : une donnée oubliée devient une erreur de
 * compilation. Le reçu actuel illustre ce que coûte l'inverse — le code y transmettait
 * {@code customerName}, {@code currentDate}, {@code orderNumber}, le gabarit attendait
 * {@code clientName}, {@code date}, {@code receiptReference}, et <b>aucun nom ne correspondait</b>.
 * Rien n'a échoué : six champs sont simplement restés blancs sur le document, pendant des mois.</p>
 *
 * @param reference       identifiant de l'encaissement côté source (référence Stripe, ou
 *                        identifiant de la ligne comptable). <b>Porte l'idempotence</b> : deux
 *                        livraisons du même webhook portent la même référence, et la seconde
 *                        ne produira pas de reçu.
 * @param motif           ce qui a été réglé
 * @param montantPaye     ce que la personne a <b>effectivement réglé</b>, jamais un total
 *                        recalculé depuis les lignes — c'est ce recalcul qui fait afficher
 *                        63 144,43 € au reçu d'une commande payée 56 829,99 €.
 * @param remise          remise déduite, {@code ZERO} s'il n'y en a pas
 * @param moyenPaiement   « Carte bancaire (Stripe) », « Virement »…
 * @param payeLe          date du règlement, pas celle de l'émission du reçu
 * @param lignes          détail de ce qui est réglé ; peut être vide (frais de dossier), jamais nul
 * @param payeurUserId    compte auquel rattacher le reçu, s'il existe
 * @param enrollmentId    dossier de formation concerné, pour le classement au coffre-fort
 * @param orderId         commande concernée, le cas échéant
 */
public record Encaissement(
        String reference,
        MotifPaiement motif,
        BigDecimal montantPaye,
        BigDecimal remise,
        String moyenPaiement,
        OffsetDateTime payeLe,
        List<LigneEncaissement> lignes,
        UUID payeurUserId,
        UUID enrollmentId,
        UUID orderId
) {

    public Encaissement {
        if (reference == null || reference.isBlank()) {
            throw new IllegalArgumentException(
                    "Un encaissement sans référence ne peut pas être rendu idempotent : "
                    + "un webhook rejoué produirait un second reçu.");
        }
        if (montantPaye == null || montantPaye.signum() <= 0) {
            throw new IllegalArgumentException("Montant réglé absent ou nul pour " + reference);
        }
        if (remise == null) remise = BigDecimal.ZERO;
        if (lignes == null) lignes = List.of();
        if (payeLe == null) payeLe = OffsetDateTime.now();
    }

    /** Une ligne du détail. */
    public record LigneEncaissement(
            String designation,
            int quantite,
            BigDecimal prixUnitaire,
            BigDecimal total
    ) {
        /** Ligne unique, pour un règlement qui n'a pas de détail (des frais, un acompte). */
        public static LigneEncaissement unique(String designation, BigDecimal montant) {
            return new LigneEncaissement(designation, 1, montant, montant);
        }
    }
}

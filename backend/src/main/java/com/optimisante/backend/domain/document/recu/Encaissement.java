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
 * @param livraison       frais de port et régime douanier, {@code null} pour un règlement qui
 *                        n'expédie rien (les frais de dossier d'une formation)
 * @param reglementEnDevise ce qui a réellement été débité, lorsque le client a payé dans une
 *                        autre devise que l'euro ; {@code null} pour un règlement en euros
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
        UUID orderId,
        Livraison livraison,
        ReglementEnDevise reglementEnDevise
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

    /**
     * Ce que le client a réellement vu débiter, quand il a payé dans une autre devise.
     *
     * <p><b>Pourquoi le détail du reçu reste en euros.</b> Convertir chaque ligne produirait un
     * document dont les lignes ne s'additionnent pas : chaque conversion s'arrondit, et leur
     * somme s'écarte du total réellement encaissé, qui est converti une seule fois. Sur une
     * pièce comptable, des lignes qui ne font pas le total est un défaut plus grave qu'un
     * détail libellé dans la devise de référence.</p>
     *
     * <p>Le document porte donc les deux : le montant débité dans sa devise — celui que la
     * personne reconnaîtra sur son relevé — et le taux qui le relie au prix en euros.</p>
     *
     * @param devise code ISO 4217 de la devise réellement débitée
     * @param montant montant débité dans cette devise
     * @param taux    combien d'unités de cette devise valaient un euro au moment du règlement
     */
    public record ReglementEnDevise(String devise, BigDecimal montant, BigDecimal taux) {}

    /**
     * Le transport, tel que le document doit l'énoncer.
     *
     * <p>Les frais de port apparaissent en pied de totaux, et non comme une ligne d'article :
     * ils ne partagent pas forcément le régime de taxe de la marchandise, et les mêler à la
     * ventilation ferait annoncer un taux qu'on n'a pas établi.</p>
     *
     * @param frais ce qui a été facturé pour le transport, {@code ZERO} si offert ou absent
     * @param dap   l'expédition quitte la France : le destinataire acquitte droits et taxes
     *              locales à l'arrivée, et le document doit le dire — pas le livreur
     */
    public record Livraison(BigDecimal frais, boolean dap) {
        public Livraison {
            if (frais == null) frais = BigDecimal.ZERO;
        }
    }

    /**
     * Une ligne du détail.
     *
     * <p>{@code tauxTva} est facultatif. Les ventes de la boutique le portent, et le reçu
     * affiche alors la ventilation que la loi attend. Un règlement dont le régime de taxe
     * n'est pas tranché — les frais de dossier d'une formation, dont le lieu d'imposition
     * dépend du parcours — le laisse nul : le reçu reste alors celui d'avant, plutôt
     * qu'annoncer une taxe qu'on n'a pas établie.</p>
     */
    public record LigneEncaissement(
            String designation,
            int quantite,
            BigDecimal prixUnitaire,
            BigDecimal total,
            BigDecimal tauxTva
    ) {
        /** Ligne unique, pour un règlement qui n'a pas de détail (des frais, un acompte). */
        public static LigneEncaissement unique(String designation, BigDecimal montant) {
            return new LigneEncaissement(designation, 1, montant, montant, null);
        }

        /** Ligne de vente : le montant est TTC, et le taux sert à en extraire la taxe. */
        public static LigneEncaissement vente(String designation, int quantite,
                                              BigDecimal prixUnitaire, BigDecimal total,
                                              BigDecimal tauxTva) {
            return new LigneEncaissement(designation, quantite, prixUnitaire, total, tauxTva);
        }
    }
}

package com.optimisante.backend.domain.orders.webhook;

import com.optimisante.backend.domain.training.finance.EnrollmentPayment;
import com.optimisante.backend.domain.training.finance.EnrollmentPaymentRepository;
import com.optimisante.backend.domain.training.finance.PaymentStatus;
import com.optimisante.backend.domain.training.finance.PaymentType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Remboursement d'un encaissement du parcours de formation.
 *
 * <p>Trois motifs passent ici : l'acompte sur frais de formation, leur solde, et les options de
 * service — assurance, hébergement, transport. Ce sont les règlements qu'un dossier interrompu
 * peut légitimement faire rendre.</p>
 *
 * <p><b>Les frais de dossier n'en font pas partie, et le code le dit.</b> Ils sont annoncés non
 * remboursables sur la page de la formation, avant tout dépôt. Un remboursement qui en viserait
 * un n'est donc pas un cas métier à traiter en silence : c'est une anomalie — geste commercial
 * non tracé, erreur de manipulation dans Stripe, ou promesse faite hors plateforme. Le montant
 * est tout de même enregistré, parce que l'argent est réellement sorti et que le registre
 * financier doit le refléter ; mais il est journalisé en <b>erreur</b>, pour qu'il remonte.</p>
 *
 * <p><b>Le remboursement partiel n'est pas représentable ici.</b> Le registre financier porte un
 * statut, pas un montant rendu : un encaissement y est réglé ou ne l'est pas. Le cas est donc
 * journalisé pour reprise manuelle, plutôt que d'inventer un état intermédiaire dont rien
 * d'autre — reversements au partenaire, commission, états financiers — ne saurait que faire.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RemboursementFormation implements TraitementRemboursement {

    private final EnrollmentPaymentRepository enrollmentPaymentRepository;

    @Override
    @Transactional
    public boolean appliquer(RemboursementConstate remboursement) {
        if (remboursement.paymentIntentId() == null) {
            return false;
        }

        EnrollmentPayment paiement = enrollmentPaymentRepository
                .findByStripePaymentIntentId(remboursement.paymentIntentId())
                .orElse(null);
        if (paiement == null) {
            return false;
        }

        if (paiement.getPaymentType() == PaymentType.DOSSIER_FEE) {
            log.error("ANOMALIE — remboursement de {} sur des FRAIS DE DOSSIER (paiement {}), "
                    + "annoncés non remboursables au candidat. Le registre est mis à jour, "
                    + "mais ce geste doit être justifié.",
                    remboursement.montantRembourse(), paiement.getId());
        }

        if (!remboursement.integral()) {
            log.warn("Remboursement PARTIEL de {} sur l'encaissement {} ({}) : le registre "
                    + "financier ne porte pas de montant rendu, statut inchangé. À reprendre "
                    + "manuellement.",
                    remboursement.montantRembourse(), paiement.getId(), paiement.getPaymentType());
            return true;
        }

        if (paiement.getStatus() == PaymentStatus.REFUNDED) {
            log.info("Encaissement {} déjà remboursé. Idempotence respectée.", paiement.getId());
            return true;
        }

        PaymentStatus precedent = paiement.getStatus();
        paiement.setStatus(PaymentStatus.REFUNDED);
        enrollmentPaymentRepository.save(paiement);

        log.info("Encaissement {} ({}) remboursé intégralement ({}) : statut {} → REFUNDED.",
                paiement.getId(), paiement.getPaymentType(),
                remboursement.montantRembourse(), precedent);

        // La commission et le reversement au partenaire ont pu etre calcules sur cet
        // encaissement. Les defaire automatiquement toucherait a de l'argent deja verse : le
        // signaler est le bon niveau d'intervention pour un webhook.
        if (paiement.getPaymentType() == PaymentType.TUITION_FEE
                && paiement.getPartnerPayout() != null) {
            log.warn("L'encaissement {} remboursé était rattaché au reversement {} : "
                    + "vérifiez la part déjà versée au partenaire.",
                    paiement.getId(), paiement.getPartnerPayout().getId());
        }
        return true;
    }
}

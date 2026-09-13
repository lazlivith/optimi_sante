package com.optimisante.backend.domain.doctorapplication.service;

import com.optimisante.backend.domain.doctorapplication.dto.DoctorApplicationResponseDto;
import com.optimisante.backend.domain.doctorapplication.entity.DoctorApplicationStatus;
import com.optimisante.backend.domain.orders.service.StripePaymentService;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Confirme une candidature payée quand le webhook Stripe ne l'a pas encore fait.
 *
 * <p><b>Pourquoi ne pas s'en remettre au seul webhook.</b> Le webhook reste le chemin normal,
 * mais rien ne garantit son heure d'arrivée : sur la base de travail, des candidatures ont été
 * confirmées 1 s, 27 s, puis 106 s après la soumission — pendant que la page de retour abandonnait
 * au bout de 20 s en annonçant « paiement en cours de confirmation ». Et en développement il
 * n'arrive pas du tout sans {@code stripe listen} : deux paiements encaissés par Stripe le
 * 13/09 sont restés en PENDING_PAYMENT, sans compte ni identifiants. Stripe recommande de traiter
 * le paiement aux deux endroits, protégé contre le double traitement — c'est ce que fait ce
 * composant, avec {@link DoctorApplicationService#confirmPayment}, idempotent et verrouillé.</p>
 *
 * <p><b>Rien n'est cru sur parole.</b> Le navigateur ne fournit qu'un identifiant de session ;
 * l'état du paiement est redemandé à Stripe par le serveur, et la session doit désigner cette
 * candidature précise ({@code client_reference_id}).</p>
 *
 * <p>Composant distinct du service, et non méthode du service : appelée depuis le service
 * lui-même, {@code confirmPayment} perdrait sa transaction — Spring n'intercepte pas un appel
 * interne à un même objet.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DoctorApplicationPaymentReconciler {

    private final DoctorApplicationService doctorApplicationService;
    private final StripePaymentService stripePaymentService;

    public DoctorApplicationResponseDto statutApresRetourStripe(String stripeSessionId) {
        DoctorApplicationResponseDto actuel =
                doctorApplicationService.getStatusByStripeCheckoutSessionId(stripeSessionId);
        if (actuel.getStatus() != DoctorApplicationStatus.PENDING_PAYMENT) {
            return actuel;
        }

        // L'appel à Stripe se fait AVANT tout verrou : on ne tient pas une ligne de base
        // bloquée le temps d'un aller-retour réseau.
        Session session;
        try {
            session = stripePaymentService.retrieveSession(stripeSessionId);
        } catch (StripeException e) {
            log.warn("Stripe injoignable pour vérifier la session {} : on s'en tient au webhook",
                    stripeSessionId, e);
            return actuel;
        }

        if (!"paid".equals(session.getPaymentStatus())) {
            return actuel;
        }
        if (!actuel.getId().toString().equals(session.getClientReferenceId())) {
            log.error("Session Stripe {} payée mais rattachée à {}, et non à la candidature {} : "
                    + "aucune confirmation.", stripeSessionId, session.getClientReferenceId(), actuel.getId());
            return actuel;
        }

        try {
            log.info("Candidature {} payée chez Stripe mais pas encore confirmée : confirmation "
                    + "depuis la page de retour.", actuel.getId());
            doctorApplicationService.confirmPayment(actuel.getId(), session.getCustomer());
        } catch (Exception e) {
            // Le webhook peut encore réussir : on n'en fait pas une erreur pour le candidat.
            log.error("Confirmation de la candidature {} depuis la page de retour impossible",
                    actuel.getId(), e);
            return actuel;
        }
        return doctorApplicationService.getStatusByStripeCheckoutSessionId(stripeSessionId);
    }
}

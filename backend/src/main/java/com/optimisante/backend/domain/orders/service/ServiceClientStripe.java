package com.optimisante.backend.domain.orders.service;

import com.optimisante.backend.domain.finance.Montant;
import com.optimisante.backend.domain.identity.entity.User;
import com.optimisante.backend.domain.identity.repository.UserRepository;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

/**
 * Ouvre une session de paiement pour un utilisateur, en garantissant que le client Stripe
 * employé existe dans l'environnement courant.
 *
 * <p><b>Deux protections, dans cet ordre, et c'est l'ordre qui compte.</b> La première évite la
 * panne : un identifiant dont la provenance ne correspond pas au mode et au compte courants est
 * écarté <b>sans appel réseau</b>, et un client neuf est créé. La seconde la rattrape : si
 * Stripe répond malgré tout que le client n'existe pas — un identifiant supprimé à la main, une
 * provenance enregistrée à tort —, on recrée et on relance <b>une seule fois</b>.</p>
 *
 * <p><b>Pourquoi une seule reprise, et pourquoi un drapeau plutôt qu'une boucle.</b> Si la
 * seconde tentative échoue encore sur le même motif, la cause n'est plus l'identifiant : c'est
 * le compte, la clé, ou Stripe lui-même. Réessayer alors ne ferait que multiplier les clients
 * créés chez Stripe à chaque clic du visiteur. Le chemin est écrit en séquence, sans récursion
 * ni boucle, pour qu'aucune évolution ultérieure ne puisse en faire une répétition.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ServiceClientStripe {

    private final StripePaymentService stripe;
    private final EnvironnementStripe environnement;
    private final UserRepository utilisateurs;

    /**
     * La session de paiement, avec un client Stripe utilisable ici et maintenant.
     *
     * @throws PaiementIndisponibleException dès que Stripe ne peut pas être employé — jamais une
     *                                       erreur brute, qui ressortirait en 400 côté réseau
     */
    public Session ouvrirSession(User utilisateur, UUID referenceId, Montant montant,
                                 String returnUrl, String libelle, Map<String, String> metadata) {
        String client = clientUtilisable(utilisateur);

        try {
            return creerSession(referenceId, montant, client, returnUrl, libelle, metadata);
        } catch (StripeException premiereTentative) {
            if (!clientIntrouvable(premiereTentative)) {
                throw new PaiementIndisponibleException(premiereTentative);
            }
            // L'identifiant passait notre contrôle de provenance mais n'existe pas chez Stripe.
            // Aucune donnée personnelle ici : l'utilisateur est désigné par son UUID interne, et
            // l'identifiant Stripe n'est pas journalisé.
            log.warn("Client Stripe introuvable pour l'utilisateur {} (provenance enregistrée : "
                            + "mode {}, compte {} ; environnement courant : mode {}, compte {}). "
                            + "Recréation et nouvelle tentative, une seule fois.",
                    utilisateur.getId(), utilisateur.getStripeCustomerMode(),
                    utilisateur.getStripeCustomerCompte(), environnement.mode(),
                    environnement.compte());

            String remplacant = creerEtEnregistrer(utilisateur);
            try {
                return creerSession(referenceId, montant, remplacant, returnUrl, libelle, metadata);
            } catch (StripeException secondeTentative) {
                // Pas de troisième essai : si un client tout juste créé est déjà introuvable,
                // le problème n'est pas l'identifiant.
                throw new PaiementIndisponibleException(secondeTentative);
            }
        }
    }

    private Session creerSession(UUID referenceId, Montant montant, String client, String returnUrl,
                                 String libelle, Map<String, String> metadata) throws StripeException {
        return stripe.createElementsCheckoutSessionForCustomer(
                referenceId, montant, client, returnUrl, libelle, metadata);
    }

    /**
     * L'identifiant à employer : celui en base s'il vient d'ici, sinon un neuf.
     *
     * <p>Un identifiant de provenance inconnue — tous ceux d'avant la migration — est traité
     * comme étranger. Le coût est une carte enregistrée perdue ; le coût inverse serait un
     * paiement impossible.</p>
     */
    private String clientUtilisable(User utilisateur) {
        String enregistre = utilisateur.getStripeCustomerId();
        if (enregistre != null && environnement.reconnait(utilisateur.getStripeCustomerMode(),
                utilisateur.getStripeCustomerCompte())) {
            return enregistre;
        }
        if (enregistre != null) {
            log.warn("Identifiant client Stripe d'un autre environnement pour l'utilisateur {} "
                            + "(enregistré : mode {}, compte {} ; courant : mode {}, compte {}). "
                            + "Ignoré, un nouveau client est créé.",
                    utilisateur.getId(), utilisateur.getStripeCustomerMode(),
                    utilisateur.getStripeCustomerCompte(), environnement.mode(),
                    environnement.compte());
        }
        return creerEtEnregistrer(utilisateur);
    }

    /**
     * Crée le client chez Stripe et enregistre les trois champs ensemble.
     *
     * <p>Les trois, toujours : un identifiant sans sa provenance serait de nouveau invérifiable,
     * et on retomberait exactement dans le défaut que cette classe corrige.</p>
     */
    private String creerEtEnregistrer(User utilisateur) {
        String identifiant;
        try {
            identifiant = stripe.createCustomer(utilisateur.getEmail());
        } catch (StripeException e) {
            throw new PaiementIndisponibleException(e);
        }
        utilisateur.setStripeCustomerId(identifiant);
        utilisateur.setStripeCustomerMode(environnement.mode());
        utilisateur.setStripeCustomerCompte(environnement.compte());
        utilisateurs.save(utilisateur);
        return identifiant;
    }

    /**
     * Stripe dit-il « ce client n'existe pas » ?
     *
     * <p>{@code resource_missing} couvre toutes les ressources absentes ; le paramètre
     * {@code customer} restreint au cas qui nous occupe. La lecture du message est un repli pour
     * les versions de la bibliothèque qui ne renseignent pas le paramètre — sans quoi un
     * identifiant mort passerait pour une panne générale et ne serait jamais remplacé.</p>
     */
    /**
     * Stripe dit-il que la ressource désignée n'existe pas ?
     *
     * <p>Vrai pour un client comme pour une session : c'est la réponse que donne un identifiant
     * créé dans un autre environnement. Ce que l'on en fait diffère — un client se recrée, une
     * session passée non.</p>
     */
    public static boolean ressourceIntrouvable(StripeException e) {
        return "resource_missing".equals(e.getCode());
    }

    static boolean clientIntrouvable(StripeException e) {
        if (!ressourceIntrouvable(e)) {
            return false;
        }
        if (e instanceof com.stripe.exception.InvalidRequestException invalide
                && "customer".equals(invalide.getParam())) {
            return true;
        }
        String message = e.getMessage();
        return message != null && message.contains("No such customer");
    }
}

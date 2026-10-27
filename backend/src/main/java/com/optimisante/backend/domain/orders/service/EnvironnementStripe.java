package com.optimisante.backend.domain.orders.service;

import com.stripe.model.Account;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Quel compte Stripe, et dans quel mode, répond à nos appels en ce moment.
 *
 * <p><b>Pourquoi cette classe existe.</b> Un identifiant client Stripe n'a de sens que dans
 * l'environnement qui l'a créé. Le 27 octobre, un changement d'environnement de test a rendu
 * invalides tous les {@code cus_…} déjà en base : la plateforme les renvoyait tels quels, et
 * Stripe répondait {@code No such customer} — un paiement impossible, pour un utilisateur qui
 * n'avait rien fait de mal. Le même mur attend le passage en mode réel, où <b>aucun</b>
 * identifiant créé en test ne sera reconnu.</p>
 *
 * <p>Effacer les identifiants à la main réparerait ce cas-ci et seulement celui-ci. Les
 * enregistrer avec leur provenance les rend vérifiables une fois pour toutes.</p>
 */
@Slf4j
@Component
public class EnvironnementStripe {

    /** Les deux modes de Stripe, tels qu'ils se lisent sur la clé. */
    public static final String MODE_TEST = "test";
    public static final String MODE_LIVE = "live";

    @Value("${stripe.api-key}")
    private String cleApi;

    private String mode;
    private String compte;

    /**
     * Le mode vient de la clé, le compte d'un appel unique au démarrage.
     *
     * <p>Le mode se lit sur le préfixe de la clé : c'est gratuit, immédiat, et il n'existe pas
     * de cas où une clé {@code sk_test_} parlerait au mode réel. Le compte, lui, demande un
     * appel réseau — fait <b>une fois ici</b> plutôt qu'à chaque paiement.</p>
     *
     * <p>Si cet appel échoue, on démarre quand même avec le mode seul. Un compte inconnu rend
     * la vérification moins fine, alors qu'un démarrage refusé empêcherait toute vente : le
     * mode seul écarte déjà le cas qui nous a coûté une panne.</p>
     */
    @PostConstruct
    void resoudre() {
        this.mode = modeDeLaCle(cleApi);
        try {
            this.compte = Account.retrieve().getId();
            log.info("Stripe : mode {}, compte {}.", mode, compte);
        } catch (Exception e) {
            this.compte = null;
            log.warn("Stripe : compte illisible, vérification limitée au mode {}. Cause : {}",
                    mode, e.getMessage());
        }
    }

    /**
     * Le mode porté par une clé secrète.
     *
     * <p>Une clé qui n'annonce ni l'un ni l'autre — une clé restreinte, un bouchon de test —
     * rend {@code null} : on ne devine pas. Un identifiant se comparant alors à {@code null}
     * sera jugé d'une autre provenance, donc remplacé. Perdre un {@code cus_…} réutilisable
     * coûte une carte enregistrée ; s'en servir à tort coûte le paiement.</p>
     */
    public static String modeDeLaCle(String cle) {
        if (cle == null) {
            return null;
        }
        if (cle.startsWith("sk_live_") || cle.startsWith("rk_live_")) {
            return MODE_LIVE;
        }
        if (cle.startsWith("sk_test_") || cle.startsWith("rk_test_")) {
            return MODE_TEST;
        }
        return null;
    }

    public String mode() {
        return mode;
    }

    /** L'identifiant {@code acct_…} du compte, ou {@code null} s'il n'a pas pu être lu. */
    public String compte() {
        return compte;
    }

    /**
     * Cet identifiant, créé dans ce mode et sur ce compte, est-il utilisable maintenant ?
     *
     * <p>Une provenance <b>inconnue</b> ({@code null}) n'est pas une provenance valable : c'est
     * le cas de tous les identifiants déjà en base avant la migration, dont nul ne sait d'où ils
     * viennent. Ils sont donc remplacés à la première occasion, et cette incertitude disparaît
     * d'elle-même sans qu'on touche aux données.</p>
     *
     * <p>Le compte ne départage que si <b>les deux</b> sont connus : quand le nôtre n'a pas pu
     * être lu, exiger qu'il corresponde rejetterait tous les identifiants, y compris les bons.</p>
     */
    public boolean reconnait(String modeEnregistre, String compteEnregistre) {
        // Par les accesseurs, jamais par les champs : ils sont redéfinissables, et une sous-classe
        // qui donne un mode sans renseigner le champ verrait tous les identifiants rejetés —
        // donc un client recréé à chaque paiement, en silence.
        if (modeEnregistre == null || !modeEnregistre.equals(mode())) {
            return false;
        }
        if (compte() != null && compteEnregistre != null) {
            return compte().equals(compteEnregistre);
        }
        return true;
    }
}

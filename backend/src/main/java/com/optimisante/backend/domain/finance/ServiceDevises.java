package com.optimisante.backend.domain.finance;

import com.optimisante.backend.config.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

/**
 * La conversion d'un prix de référence vers une devise de présentation.
 *
 * <p><b>Le modèle en une phrase :</b> un prix existe une fois, en euros, et toute autre devise
 * s'en déduit par un taux que l'administration tient. Vendre en franc CFA ne demande donc pas
 * de ressaisir le catalogue — ce qui, à chaque ajout de produit, aurait laissé des articles
 * invendables dans une zone sans que rien ne le signale.</p>
 *
 * <p><b>L'arrondi remonte, toujours.</b> Deux raisons. Un prix converti qui descendrait ferait
 * encaisser moins que le prix de référence, à chaque vente ; et un montant au palier se lit —
 * 328 000 FCFA plutôt que 327 978,5, qui n'existe d'ailleurs pas, le franc CFA n'ayant pas de
 * subdivision.</p>
 *
 * <p><b>Sur le cas qui nous occupe, le taux n'est pas une cotation.</b> Le franc CFA est à
 * parité fixe avec l'euro — 1 EUR = 655,957 XAF, garantie par le Trésor français. Il n'y a donc
 * aucun risque de change à couvrir, et aucun flux de cotation à brancher : le taux est une
 * constante que l'administration pose une fois.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ServiceDevises {

    private final TauxChangeRepository tauxChangeRepository;

    /** La grille complète, euro compris, telle que l'écran d'administration l'affiche. */
    @Transactional(readOnly = true)
    public List<TauxChange> grille() {
        return tauxChangeRepository.findByTenantIdOrderByDeviseAsc(tenantRequis());
    }

    /** Les devises qu'un client peut effectivement choisir. */
    @Transactional(readOnly = true)
    public List<Devise> devisesActives() {
        return tauxChangeRepository.findByTenantIdAndActifTrueOrderByDeviseAsc(tenantRequis())
                .stream().map(t -> Devise.de(t.getDevise())).toList();
    }

    /**
     * Convertit un montant de référence vers une devise de présentation.
     *
     * <p>Rendre le montant tel quel quand la devise cible est l'euro n'est pas une optimisation :
     * c'est la garantie qu'un prix en euros ne subit jamais d'arrondi, même si quelqu'un venait
     * à poser un palier sur la ligne de l'euro.</p>
     *
     * @param reference montant en euros ; une autre devise est refusée, car convertir depuis une
     *                  devise de présentation reviendrait à enchaîner deux arrondis
     * @throws IllegalArgumentException si la devise cible n'est pas servie ou n'est pas active
     */
    @Transactional(readOnly = true)
    public Montant convertir(Montant reference, Devise cible) {
        if (!reference.devise().estReference()) {
            throw new IllegalArgumentException(
                    "La conversion part du prix de référence, en " + Devise.REFERENCE
                    + ", et non de " + reference.devise() + " : enchaîner deux conversions "
                    + "accumulerait deux arrondis.");
        }
        if (cible.estReference()) {
            return reference;
        }

        TauxChange taux = tauxChangeRepository
                .findByTenantIdAndDevise(tenantRequis(), cible.code())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Aucun taux n'est défini pour la devise " + cible + "."));
        if (!Boolean.TRUE.equals(taux.getActif())) {
            throw new IllegalArgumentException(
                    "La devise " + cible + " n'est pas ouverte à la vente.");
        }

        BigDecimal converti = reference.valeur().multiply(taux.getTaux());
        return new Montant(auPalierSuperieur(converti, taux.getPalierArrondi()), cible);
    }

    /**
     * Le plus petit multiple du palier qui atteint ce montant.
     *
     * <p>{@code ceil(valeur / palier) * palier}. Avec un palier de 1 000, 327 978,5 donne
     * 328 000 ; avec le palier de 0,01 de l'euro, la valeur ne bouge pas.</p>
     */
    static BigDecimal auPalierSuperieur(BigDecimal valeur, BigDecimal palier) {
        if (palier == null || palier.signum() <= 0) {
            return valeur;
        }
        return valeur.divide(palier, 0, RoundingMode.CEILING).multiply(palier);
    }

    /**
     * Enregistre le taux d'une devise.
     *
     * <p>L'euro n'est pas modifiable : il est la référence, son taux vaut un par définition, et
     * le laisser changer rendrait tous les prix de la plateforme dépendants d'une saisie.</p>
     */
    @Transactional
    public TauxChange enregistrer(Devise devise, BigDecimal taux, BigDecimal palier, boolean actif) {
        if (devise.estReference()) {
            throw new IllegalArgumentException(
                    "L'euro est la devise de référence : son taux vaut 1 et ne se modifie pas.");
        }
        if (taux == null || taux.signum() <= 0) {
            throw new IllegalArgumentException("Le taux doit être strictement positif.");
        }
        if (palier == null || palier.signum() <= 0) {
            throw new IllegalArgumentException("Le palier d'arrondi doit être strictement positif.");
        }

        UUID tenantId = tenantRequis();
        TauxChange ligne = tauxChangeRepository.findByTenantIdAndDevise(tenantId, devise.code())
                .orElseGet(() -> TauxChange.builder()
                        .tenantId(tenantId).devise(devise.code()).build());

        ligne.setTaux(taux);
        ligne.setPalierArrondi(palier);
        ligne.setActif(actif);
        ligne.setUpdatedAt(java.time.OffsetDateTime.now());

        log.info("Taux de change {} : 1 EUR = {} {}, palier {}, {}",
                devise, taux, devise, palier, actif ? "ouverte à la vente" : "fermée");
        return tauxChangeRepository.save(ligne);
    }

    private UUID tenantRequis() {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new IllegalStateException("Tenant context is required");
        }
        return tenantId;
    }
}

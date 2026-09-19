package com.optimisante.backend.domain.catalog.supplier;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Du prix d'achat au prix de vente.
 *
 * <p><b>Le métier.</b> Optimi Santé achète en gros à ses fournisseurs et revend à des hôpitaux,
 * cliniques et pharmacies. Le fichier déposé porte donc un prix d'achat, et le prix public s'en
 * déduit par une marge.</p>
 *
 * <p><b>Deux marges, dans cet ordre.</b> Celle de la catégorie du produit d'abord — une marge se
 * décide par famille, un antalgique et un tensiomètre ne se marient pas au même taux — puis, à
 * défaut, la commission négociée avec le fournisseur. Sans l'une ni l'autre, le prix d'achat sert
 * de prix de vente : mieux vaut un prix juste sans marge qu'un prix inventé, et l'analyse le
 * signale avant toute écriture.</p>
 *
 * <p><b>Le fichier garde le dernier mot.</b> Un fournisseur qui fournit déjà un prix de vente
 * conseillé (colonne {@code prix_vente}) l'impose : la marge ne sert qu'à combler son absence.</p>
 */
public final class MargeCatalogue {

    /** D'où vient la marge appliquée, pour l'expliquer à l'écran. */
    public enum Origine {
        CATEGORIE, FOURNISSEUR, AUCUNE, PRIX_DE_VENTE_FOURNI
    }

    /**
     * @param prixVente montant porté au catalogue
     * @param taux      marge appliquée, en pourcentage ; zéro quand aucune ne s'applique
     */
    public record Calcul(BigDecimal prixVente, BigDecimal taux, Origine origine) {
    }

    private MargeCatalogue() {
    }

    /**
     * @param prixVenteFourni prix de vente lu dans le fichier, ou {@code null}
     * @param prixAchat       prix d'achat lu dans le fichier, ou {@code null}
     * @param margeCategorie  marge de la catégorie visée, ou {@code null}
     * @param margeFournisseur commission négociée avec le fournisseur, jamais nulle
     */
    public static Calcul calculer(BigDecimal prixVenteFourni, BigDecimal prixAchat,
                                  BigDecimal margeCategorie, BigDecimal margeFournisseur) {
        if (prixVenteFourni != null) {
            return new Calcul(arrondi(prixVenteFourni), BigDecimal.ZERO, Origine.PRIX_DE_VENTE_FOURNI);
        }
        if (prixAchat == null) {
            throw new IllegalArgumentException("Ni prix de vente ni prix d'achat.");
        }
        if (positif(margeCategorie)) {
            return new Calcul(majorer(prixAchat, margeCategorie), margeCategorie, Origine.CATEGORIE);
        }
        if (positif(margeFournisseur)) {
            return new Calcul(majorer(prixAchat, margeFournisseur), margeFournisseur, Origine.FOURNISSEUR);
        }
        return new Calcul(arrondi(prixAchat), BigDecimal.ZERO, Origine.AUCUNE);
    }

    private static BigDecimal majorer(BigDecimal prixAchat, BigDecimal taux) {
        BigDecimal coefficient = BigDecimal.ONE.add(taux.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP));
        return arrondi(prixAchat.multiply(coefficient));
    }

    /** Deux décimales, arrondi commercial : c'est le montant qui sera facturé, pas une estimation. */
    private static BigDecimal arrondi(BigDecimal montant) {
        return montant.setScale(2, RoundingMode.HALF_UP);
    }

    private static boolean positif(BigDecimal taux) {
        return taux != null && taux.signum() > 0;
    }
}

package com.optimisante.backend.domain.catalog.supplier;

import com.optimisante.backend.domain.catalog.entity.SlugValide;

import java.math.BigDecimal;

/**
 * Ce qu'une ligne de fichier fournisseur doit tenir pour être écrite.
 *
 * <p><b>Pourquoi vérifier avant d'écrire, plutôt que de rattraper l'erreur après.</b> Une
 * écriture qui échoue en cours de transaction empoisonne la session JPA : les lignes suivantes
 * du même lot échouent à leur tour, quelles qu'elles soient. Le 23 octobre, une seule ligne
 * fautive à la trois-millième aurait perdu son lot de cinquante <b>et</b> les 4 400 lignes
 * suivantes, l'import entier passant en échec.</p>
 *
 * <p>Valider d'abord permet d'écarter la ligne, de dire pourquoi avec son numéro dans le
 * fichier, et de continuer. C'est le canal des motifs qui existe déjà pour les refus métier —
 * marge absente, catégorie inconnue —, utilisé ici pour les refus de forme.</p>
 */
public final class ValidationLigneCatalogue {

    /** Au-delà, la colonne du fichier déborde la base ({@code varchar(255)}). */
    private static final int NOM_MAX = 255;

    private ValidationLigneCatalogue() {
    }

    /**
     * Le motif de refus, ou {@code null} si la ligne est écrivable.
     *
     * @param slugPressenti le slug que la ligne produirait, déjà construit par l'appelant :
     *                      c'est lui qu'il faut juger, pas le nom brut dont il dérive
     */
    public static String refus(FichierCatalogue.Ligne ligne, String slugPressenti) {
        if (estVide(ligne.sku())) {
            return "référence (SKU) absente.";
        }
        if (estVide(ligne.nom())) {
            return "nom de produit absent.";
        }
        if (ligne.nom().length() > NOM_MAX) {
            return "nom de " + ligne.nom().length() + " caractères, au-delà des " + NOM_MAX + " admis.";
        }
        // Le garde-fou JPA refuserait de toute façon l'enregistrement, mais il le ferait par une
        // exception, au flush, donc après avoir condamné le lot entier. Le même test ici écarte
        // la seule ligne en cause.
        if (slugPressenti == null || !SlugValide.MOTIF.matcher(slugPressenti).matches()) {
            return "le nom ne produit aucune adresse exploitable : « " + slugPressenti + " ».";
        }
        if (ligne.stock() != null && ligne.stock() < 0) {
            return "quantité en stock négative (" + ligne.stock() + ").";
        }
        if (negatif(ligne.prixAchat())) {
            return "prix d'achat négatif (" + ligne.prixAchat() + ").";
        }
        if (negatif(ligne.prix())) {
            return "prix de vente négatif (" + ligne.prix() + ").";
        }
        return null;
    }

    private static boolean estVide(String valeur) {
        return valeur == null || valeur.isBlank();
    }

    private static boolean negatif(BigDecimal montant) {
        return montant != null && montant.signum() < 0;
    }
}

package com.optimisante.backend.domain.catalog.supplier;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static com.optimisante.backend.domain.catalog.supplier.MargeCatalogue.Origine;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Du prix d'achat au prix de vente : c'est ici que se joue la marge d'Optimi Santé sur chaque
 * référence importée. Une erreur passe inaperçue à l'écran et se voit sur la facture.
 */
class MargeCatalogueTest {

    private static final BigDecimal ACHAT = new BigDecimal("1200.00");

    /**
     * Taux nul : ces cas éprouvent la MARGE, pas la taxe.
     *
     * <p>Les séparer est délibéré — mêler les deux ferait d'un écart de marge et d'un écart de
     * taux la même défaillance, et le test ne dirait plus lequel des deux a bougé.</p>
     */
    private static final BigDecimal SANS_TVA = BigDecimal.ZERO;

    @Test
    void laMargeDeLaCategoriePrimeSurCelleDuFournisseur() {
        var calcul = MargeCatalogue.calculer(null, ACHAT, new BigDecimal("30"), new BigDecimal("12.5"), SANS_TVA);
        assertEquals(new BigDecimal("1560.00"), calcul.prixVente());
        assertEquals(Origine.CATEGORIE, calcul.origine());
        assertEquals(new BigDecimal("30"), calcul.taux());
    }

    @Test
    void sansMargeDeCategorieOnPrendCelleDuFournisseur() {
        var calcul = MargeCatalogue.calculer(null, ACHAT, null, new BigDecimal("12.5"), SANS_TVA);
        assertEquals(new BigDecimal("1350.00"), calcul.prixVente());
        assertEquals(Origine.FOURNISSEUR, calcul.origine());
    }

    @Test
    void uneMargeDeCategorieAZeroNeBloquePasCelleDuFournisseur() {
        // 0 % saisi vaut « pas de taux pour cette famille », pas « vendre à prix coûtant » :
        // sans cette lecture, une catégorie créée puis laissée à zéro annulerait toute marge.
        var calcul = MargeCatalogue.calculer(null, ACHAT, BigDecimal.ZERO, new BigDecimal("12.5"), SANS_TVA);
        assertEquals(Origine.FOURNISSEUR, calcul.origine());
    }

    @Test
    void aucuneMargeNulLePrixDAchatSertDePrixDeVente() {
        var calcul = MargeCatalogue.calculer(null, ACHAT, null, BigDecimal.ZERO, SANS_TVA);
        assertEquals(new BigDecimal("1200.00"), calcul.prixVente());
        assertEquals(Origine.AUCUNE, calcul.origine());
    }

    @Test
    void laTaxeSAjouteApresLaMarge() {
        // Le defaut que ce parametre corrige : le fournisseur facture 1 200 EUR HORS TAXES, le
        // catalogue affiche du TTC. S'arreter a la marge mettait 1 560 EUR en boutique au lieu
        // de 1 872 — un cinquieme de moins, sur chaque produit importe.
        var calcul = MargeCatalogue.calculer(null, ACHAT, new BigDecimal("30"),
                new BigDecimal("12.5"), new BigDecimal("20"));
        assertEquals(new BigDecimal("1872.00"), calcul.prixVente());
        // Le taux rendu reste celui de la MARGE : c'est lui que l'ecran annonce.
        assertEquals(new BigDecimal("30"), calcul.taux());
    }

    @Test
    void leTauxReduitDonneUnPrixPlusBas() {
        // Un fauteuil roulant releve de 5,5 % : meme marge, prix public moindre.
        var calcul = MargeCatalogue.calculer(null, ACHAT, new BigDecimal("30"),
                BigDecimal.ZERO, new BigDecimal("5.5"));
        assertEquals(new BigDecimal("1645.80"), calcul.prixVente());
    }

    @Test
    void sansMargeLePrixDAchatEstTOUT_DE_MEME_taxe() {
        // Sans marge, le prix d'achat sert de prix de vente — mais il reste hors taxes, et le
        // porter tel quel annoncerait un prix que la caisse ne retrouverait pas.
        var calcul = MargeCatalogue.calculer(null, ACHAT, null, BigDecimal.ZERO,
                new BigDecimal("20"));
        assertEquals(new BigDecimal("1440.00"), calcul.prixVente());
        assertEquals(Origine.AUCUNE, calcul.origine());
    }

    @Test
    void unPrixDeVenteFourniNEstPasTaxeUneSecondeFois() {
        // On ne sait pas si le prix conseille du fournisseur est deja taxe : lui ajouter la
        // taxe reviendrait a parier. Il est pris tel quel, comme avant.
        var calcul = MargeCatalogue.calculer(new BigDecimal("1999.90"), ACHAT,
                new BigDecimal("30"), BigDecimal.ZERO, new BigDecimal("20"));
        assertEquals(new BigDecimal("1999.90"), calcul.prixVente());
        assertEquals(Origine.PRIX_DE_VENTE_FOURNI, calcul.origine());
    }

    @Test
    void unPrixDeVenteDansLeFichierSImpose() {
        var calcul = MargeCatalogue.calculer(new BigDecimal("1999.9"), ACHAT, new BigDecimal("30"), new BigDecimal("12.5"), SANS_TVA);
        assertEquals(new BigDecimal("1999.90"), calcul.prixVente());
        assertEquals(Origine.PRIX_DE_VENTE_FOURNI, calcul.origine());
    }

    @Test
    void leMontantEstArrondiAuCentimeCarIlSeraFacture() {
        var calcul = MargeCatalogue.calculer(null, new BigDecimal("1199.99"), new BigDecimal("17.5"), BigDecimal.ZERO, SANS_TVA);
        assertEquals(new BigDecimal("1409.99"), calcul.prixVente());
        assertEquals(2, calcul.prixVente().scale());
    }

    @Test
    void sansAucunPrixLeCalculEstImpossible() {
        assertThrows(IllegalArgumentException.class,
                () -> MargeCatalogue.calculer(null, null, null, BigDecimal.TEN, SANS_TVA));
    }
}

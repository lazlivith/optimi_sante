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

    @Test
    void laMargeDeLaCategoriePrimeSurCelleDuFournisseur() {
        var calcul = MargeCatalogue.calculer(null, ACHAT, new BigDecimal("30"), new BigDecimal("12.5"));
        assertEquals(new BigDecimal("1560.00"), calcul.prixVente());
        assertEquals(Origine.CATEGORIE, calcul.origine());
        assertEquals(new BigDecimal("30"), calcul.taux());
    }

    @Test
    void sansMargeDeCategorieOnPrendCelleDuFournisseur() {
        var calcul = MargeCatalogue.calculer(null, ACHAT, null, new BigDecimal("12.5"));
        assertEquals(new BigDecimal("1350.00"), calcul.prixVente());
        assertEquals(Origine.FOURNISSEUR, calcul.origine());
    }

    @Test
    void uneMargeDeCategorieAZeroNeBloquePasCelleDuFournisseur() {
        // 0 % saisi vaut « pas de taux pour cette famille », pas « vendre à prix coûtant » :
        // sans cette lecture, une catégorie créée puis laissée à zéro annulerait toute marge.
        var calcul = MargeCatalogue.calculer(null, ACHAT, BigDecimal.ZERO, new BigDecimal("12.5"));
        assertEquals(Origine.FOURNISSEUR, calcul.origine());
    }

    @Test
    void aucuneMargeNulLePrixDAchatSertDePrixDeVente() {
        var calcul = MargeCatalogue.calculer(null, ACHAT, null, BigDecimal.ZERO);
        assertEquals(new BigDecimal("1200.00"), calcul.prixVente());
        assertEquals(Origine.AUCUNE, calcul.origine());
    }

    @Test
    void unPrixDeVenteDansLeFichierSImpose() {
        var calcul = MargeCatalogue.calculer(new BigDecimal("1999.9"), ACHAT, new BigDecimal("30"), new BigDecimal("12.5"));
        assertEquals(new BigDecimal("1999.90"), calcul.prixVente());
        assertEquals(Origine.PRIX_DE_VENTE_FOURNI, calcul.origine());
    }

    @Test
    void leMontantEstArrondiAuCentimeCarIlSeraFacture() {
        var calcul = MargeCatalogue.calculer(null, new BigDecimal("1199.99"), new BigDecimal("17.5"), BigDecimal.ZERO);
        assertEquals(new BigDecimal("1409.99"), calcul.prixVente());
        assertEquals(2, calcul.prixVente().scale());
    }

    @Test
    void sansAucunPrixLeCalculEstImpossible() {
        assertThrows(IllegalArgumentException.class, () -> MargeCatalogue.calculer(null, null, null, BigDecimal.TEN));
    }
}

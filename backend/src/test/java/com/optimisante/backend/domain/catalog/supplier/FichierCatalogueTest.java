package com.optimisante.backend.domain.catalog.supplier;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Lecture d'un catalogue fournisseur : ce sont les fichiers réels qui dictent ces cas — séparateur
 * variable, accents, virgule décimale, BOM d'Excel, intitulés de colonnes libres.
 */
class FichierCatalogueTest {

    private static List<FichierCatalogue.Ligne> lire(String csv) {
        return FichierCatalogue.lire(csv.getBytes(StandardCharsets.UTF_8), "catalogue.csv").lignes();
    }

    @Test
    void litUnFichierPointVirguleAvecAccentsEtVirguleDecimale() {
        var lignes = lire("""
                sku;nom;description;prix;stock;categorie;image_urls
                PROD-001;Tensiomètre Omron;Brassard 22-42 cm;45 000,50;15;Matériel médical;https://ex.fr/a.jpg
                """);
        assertEquals(1, lignes.size());
        FichierCatalogue.Ligne ligne = lignes.get(0);
        assertNull(ligne.erreur());
        assertEquals("PROD-001", ligne.sku());
        assertEquals("Tensiomètre Omron", ligne.nom());
        assertEquals(new BigDecimal("45000.50"), ligne.prix());
        assertEquals(15, ligne.stock());
        assertEquals("Matériel médical", ligne.categorie());
        assertEquals(List.of("https://ex.fr/a.jpg"), ligne.images());
    }

    @Test
    void accepteLaVirguleCommeSeparateurEtLesGuillemets() {
        var lignes = lire("""
                sku,nom,prix,stock
                PROD-002,"Gants nitrile, boîte de 100",9500,240
                """);
        assertEquals("Gants nitrile, boîte de 100", lignes.get(0).nom());
        assertEquals(new BigDecimal("9500.00"), lignes.get(0).prix());
    }

    @Test
    void accepteLeBomDExcelEtDesIntitulesDeColonnesDifferents() {
        var lignes = lire("﻿Référence;Désignation;Prix HT (€);Quantité;Lien_Photo\n"
                + "PROD-003;Thermomètre;1 500;12;https://ex.fr/t.jpg\n");
        assertNull(lignes.get(0).erreur());
        assertEquals("PROD-003", lignes.get(0).sku());
        assertEquals("Thermomètre", lignes.get(0).nom());
        assertEquals(new BigDecimal("1500.00"), lignes.get(0).prix());
        assertEquals(12, lignes.get(0).stock());
    }

    @Test
    void separePlusieursImagesEtEcarteCeQuiNestPasUneAdresse() {
        var lignes = lire("""
                sku;nom;prix;images
                PROD-004;Otoscope;12000;https://ex.fr/1.jpg|https://ex.fr/2.jpg|photo-locale.jpg
                """);
        assertEquals(List.of("https://ex.fr/1.jpg", "https://ex.fr/2.jpg"), lignes.get(0).images());
    }

    @Test
    void refuseLesLignesInexploitablesEnDisantPourquoi() {
        var lignes = lire("""
                sku;nom;prix
                ;Sans référence;1000
                PROD-005;;2000
                PROD-006;Sans prix;
                PROD-007;Prix illisible;à négocier
                PROD-008;Prix négatif;-10
                """);
        assertEquals(5, lignes.size());
        assertTrue(lignes.get(0).erreur().contains("Référence"));
        assertTrue(lignes.get(1).erreur().contains("Nom"));
        assertTrue(lignes.get(2).erreur().contains("Prix absent"));
        assertTrue(lignes.get(3).erreur().contains("illisible"));
        assertTrue(lignes.get(4).erreur().contains("négatif"));
        // Le numéro affiché est celui du tableur : l'en-tête est la ligne 1.
        assertEquals(2, lignes.get(0).numero());
    }

    @Test
    void ignoreLesLignesVidesDeFinDeFichier() {
        assertEquals(1, lire("sku;nom;prix\nPROD-009;Table d'examen;89000\n;;\n\n").size());
    }

    @Test
    void refuseUnFichierSansLesColonnesIndispensables() {
        var erreur = assertThrows(IllegalArgumentException.class,
                () -> lire("designation;tarif\nTensiomètre;45000\n"));
        assertTrue(erreur.getMessage().contains("sku"));
    }

    @Test
    void litAussiUnClasseurExcel() throws Exception {
        byte[] classeur = classeurExcel();
        var lignes = FichierCatalogue.lire(classeur, "catalogue.xlsx").lignes();
        assertEquals(1, lignes.size());
        assertEquals("PROD-010", lignes.get(0).sku());
        assertEquals(new BigDecimal("2500.00"), lignes.get(0).prix());
        assertEquals(7, lignes.get(0).stock());
    }

    @Test
    void lesMontantsSuiventLaConventionDuFichier() {
        assertEquals(new BigDecimal("1500.50"), FichierCatalogue.montant("1.500,50"));
        assertEquals(new BigDecimal("1500.50"), FichierCatalogue.montant("1,500.50"));
        assertEquals(new BigDecimal("45000.00"), FichierCatalogue.montant("45 000 FCFA"));
        assertNull(FichierCatalogue.montant("  "));
        assertThrows(NumberFormatException.class, () -> FichierCatalogue.montant("gratuit"));
    }

    private static byte[] classeurExcel() throws Exception {
        try (XSSFWorkbook classeur = new XSSFWorkbook(); ByteArrayOutputStream sortie = new ByteArrayOutputStream()) {
            Sheet feuille = classeur.createSheet("Catalogue");
            Row entete = feuille.createRow(0);
            String[] colonnes = {"sku", "nom", "prix", "stock"};
            for (int i = 0; i < colonnes.length; i++) {
                entete.createCell(i).setCellValue(colonnes[i]);
            }
            Row ligne = feuille.createRow(1);
            ligne.createCell(0).setCellValue("PROD-010");
            ligne.createCell(1).setCellValue("Stéthoscope");
            ligne.createCell(2).setCellValue(2500);
            ligne.createCell(3).setCellValue(7);
            classeur.write(sortie);
            return sortie.toByteArray();
        }
    }
}

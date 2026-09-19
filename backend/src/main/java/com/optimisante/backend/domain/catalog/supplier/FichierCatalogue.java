package com.optimisante.backend.domain.catalog.supplier;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.*;

/**
 * Lecture d'un catalogue fournisseur, en CSV ou en Excel.
 *
 * <p><b>Les fournisseurs n'écrivent pas tous pareil.</b> Séparateur point-virgule ou virgule,
 * en-tête « prix », « prix_ht » ou « price », accents et majuscules au hasard, virgule décimale,
 * espaces insécables dans les montants, BOM en tête de fichier : tout cela arrive, et refuser le
 * fichier pour cette raison reviendrait à ressaisir deux mille lignes à la main. On reconnaît donc
 * les colonnes par leurs noms usuels et on normalise les valeurs.</p>
 *
 * <p><b>Ce qui n'est pas deviné.</b> Une ligne sans référence, sans nom ou sans prix lisible est
 * refusée avec son numéro et son motif : inventer une valeur mettrait un prix faux en boutique.</p>
 */
public final class FichierCatalogue {

    /** Au-delà, le fichier relève d'un chargement par lots, pas d'un dépôt dans un navigateur. */
    public static final int LIGNES_MAX = 20_000;
    public static final long TAILLE_MAX_OCTETS = 15L * 1024 * 1024;
    public static final int IMAGES_MAX_PAR_PRODUIT = 5;

    /** Noms de colonnes acceptés, par champ. Le premier de chaque liste est celui du modèle. */
    private static final Map<String, List<String>> COLONNES = Map.of(
            "sku", List.of("sku", "reference", "ref", "code", "code_produit"),
            "nom", List.of("nom", "name", "libelle", "designation", "intitule"),
            "description", List.of("description", "desc", "details"),
            // Deux prix distincts : ce que le fournisseur facture, et ce que la boutique affiche.
            // « prix » seul reste un prix de vente, pour ne pas changer le sens des fichiers déjà déposés.
            "prix_achat", List.of("prix_achat_ht", "prix_achat", "prix_achat_ttc", "prix_d_achat", "prix_d_achat_ht",
                    "achat", "cout", "cout_achat", "prix_fournisseur", "purchase_price", "cost"),
            "prix", List.of("prix", "prix_ht", "prix_ttc", "price", "tarif", "prix_vente", "prix_public"),
            "stock", List.of("stock", "quantite", "qty", "quantity", "stock_quantity"),
            "categorie", List.of("categorie", "famille", "category", "rayon"),
            "images", List.of("image_urls", "image_url", "images", "image", "photo", "lien_photo", "photos"));

    private FichierCatalogue() {
    }

    /**
     * Une ligne lue. {@code erreur} non nulle signifie que la ligne est refusée ; les autres
     * champs peuvent alors être incomplets.
     */
    public record Ligne(int numero, String sku, String nom, String description, BigDecimal prix,
                        BigDecimal prixAchat, Integer stock, String categorie, List<String> images,
                        String erreur) {

        static Ligne refusee(int numero, String sku, String motif) {
            return new Ligne(numero, sku, null, null, null, null, null, null, List.of(), motif);
        }
    }

    public record Lecture(List<Ligne> lignes, List<String> colonnesReconnues) {
    }

    /** @throws IllegalArgumentException si le fichier est illisible ou sans colonne exploitable */
    public static Lecture lire(byte[] contenu, String nomFichier) {
        String nom = nomFichier == null ? "" : nomFichier.toLowerCase(Locale.ROOT);
        List<List<String>> cellules = nom.endsWith(".xlsx") || nom.endsWith(".xls")
                ? lireExcel(contenu)
                : lireCsv(contenu);

        if (cellules.isEmpty()) {
            throw new IllegalArgumentException("Le fichier est vide.");
        }
        Map<String, Integer> index = reconnaitreColonnes(cellules.get(0));
        if (!index.containsKey("sku") || !index.containsKey("nom")) {
            throw new IllegalArgumentException("Colonnes « sku » et « nom » introuvables dans l'en-tête. "
                    + "Téléchargez le modèle pour retrouver les colonnes attendues.");
        }
        if (cellules.size() - 1 > LIGNES_MAX) {
            throw new IllegalArgumentException("Le fichier dépasse " + LIGNES_MAX + " lignes.");
        }

        List<Ligne> lignes = new ArrayList<>();
        for (int i = 1; i < cellules.size(); i++) {
            List<String> ligne = cellules.get(i);
            if (ligne.stream().allMatch(c -> c == null || c.isBlank())) {
                continue; // ligne vide en fin de fichier : ce n'est pas une erreur
            }
            lignes.add(construire(i + 1, ligne, index));
        }
        return new Lecture(lignes, new ArrayList<>(index.keySet()));
    }

    private static Ligne construire(int numero, List<String> ligne, Map<String, Integer> index) {
        String sku = valeur(ligne, index, "sku");
        String nom = valeur(ligne, index, "nom");
        if (sku == null || sku.isBlank()) {
            return Ligne.refusee(numero, null, "Référence (sku) absente.");
        }
        if (nom == null || nom.isBlank()) {
            return Ligne.refusee(numero, sku, "Nom du produit absent.");
        }

        BigDecimal prix;
        BigDecimal prixAchat;
        try {
            prix = montant(valeur(ligne, index, "prix"));
        } catch (NumberFormatException e) {
            return Ligne.refusee(numero, sku, "Prix de vente illisible : « " + valeur(ligne, index, "prix") + " ».");
        }
        try {
            prixAchat = montant(valeur(ligne, index, "prix_achat"));
        } catch (NumberFormatException e) {
            return Ligne.refusee(numero, sku, "Prix d'achat illisible : « " + valeur(ligne, index, "prix_achat") + " ».");
        }
        if (prix == null && prixAchat == null) {
            return Ligne.refusee(numero, sku, "Prix absent : indiquez un prix d'achat ou un prix de vente.");
        }
        if ((prix != null && prix.signum() < 0) || (prixAchat != null && prixAchat.signum() < 0)) {
            return Ligne.refusee(numero, sku, "Prix négatif.");
        }

        Integer stock;
        try {
            stock = entier(valeur(ligne, index, "stock"));
        } catch (NumberFormatException e) {
            return Ligne.refusee(numero, sku, "Stock illisible : « " + valeur(ligne, index, "stock") + " ».");
        }
        if (stock != null && stock < 0) {
            return Ligne.refusee(numero, sku, "Stock négatif.");
        }

        return new Ligne(numero, sku.trim(), nom.trim(), valeur(ligne, index, "description"), prix, prixAchat,
                stock, valeur(ligne, index, "categorie"), images(valeur(ligne, index, "images")), null);
    }

    /** Plusieurs images dans une seule cellule, séparées par « | », « , » ou un espace. */
    private static List<String> images(String brut) {
        if (brut == null || brut.isBlank()) {
            return List.of();
        }
        return Arrays.stream(brut.split("[|,;\\s]+"))
                .map(String::trim)
                .filter(u -> u.startsWith("http://") || u.startsWith("https://"))
                .distinct()
                .limit(IMAGES_MAX_PAR_PRODUIT)
                .toList();
    }

    private static String valeur(List<String> ligne, Map<String, Integer> index, String champ) {
        Integer colonne = index.get(champ);
        if (colonne == null || colonne >= ligne.size()) {
            return null;
        }
        String v = ligne.get(colonne);
        return v == null || v.isBlank() ? null : v.trim();
    }

    /** « 1 500,50 », « 1.500,50 », « 1500.50 » ou « 45 000 FCFA » donnent tous un montant. */
    static BigDecimal montant(String brut) {
        if (brut == null || brut.isBlank()) {
            return null;
        }
        String nettoye = brut.replaceAll("[^0-9,.\\-]", "");
        if (nettoye.isBlank() || "-".equals(nettoye)) {
            throw new NumberFormatException(brut);
        }
        int virgule = nettoye.lastIndexOf(',');
        int point = nettoye.lastIndexOf('.');
        // Le dernier séparateur rencontré est le séparateur décimal ; l'autre sépare les milliers.
        if (virgule >= 0 && virgule > point) {
            nettoye = nettoye.replace(".", "").replace(',', '.');
        } else {
            nettoye = nettoye.replace(",", "");
        }
        return new BigDecimal(nettoye).setScale(2, java.math.RoundingMode.HALF_UP);
    }

    static Integer entier(String brut) {
        if (brut == null || brut.isBlank()) {
            return null;
        }
        String nettoye = brut.replaceAll("[^0-9\\-]", "");
        if (nettoye.isBlank() || "-".equals(nettoye)) {
            throw new NumberFormatException(brut);
        }
        return Integer.valueOf(nettoye);
    }

    private static Map<String, Integer> reconnaitreColonnes(List<String> entete) {
        Map<String, Integer> index = new LinkedHashMap<>();
        for (int i = 0; i < entete.size(); i++) {
            String cle = normaliser(entete.get(i));
            for (Map.Entry<String, List<String>> entree : COLONNES.entrySet()) {
                if (entree.getValue().contains(cle)) {
                    // `putIfAbsent` : si le fichier porte deux colonnes de prix, la première gagne.
                    index.putIfAbsent(entree.getKey(), i);
                }
            }
        }
        return index;
    }

    /** « Prix HT (€) » devient « prix_ht » : accents, casse et ponctuation ne doivent pas compter. */
    private static String normaliser(String entete) {
        if (entete == null) {
            return "";
        }
        String sansAccents = Normalizer.normalize(entete, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sansAccents.toLowerCase(Locale.ROOT)
                .replaceAll("\\(.*?\\)", "")
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
    }

    // ------------------------------------------------------------------------ CSV ----

    private static List<List<String>> lireCsv(byte[] contenu) {
        String texte = new String(contenu, StandardCharsets.UTF_8);
        if (!texte.isEmpty() && texte.charAt(0) == '﻿') {
            texte = texte.substring(1); // BOM déposé par Excel
        }
        char separateur = separateur(texte);
        List<List<String>> lignes = new ArrayList<>();
        List<String> ligne = new ArrayList<>();
        StringBuilder cellule = new StringBuilder();
        boolean entreGuillemets = false;

        for (int i = 0; i < texte.length(); i++) {
            char c = texte.charAt(i);
            if (entreGuillemets) {
                if (c == '"') {
                    if (i + 1 < texte.length() && texte.charAt(i + 1) == '"') {
                        cellule.append('"');
                        i++;
                    } else {
                        entreGuillemets = false;
                    }
                } else {
                    cellule.append(c);
                }
            } else if (c == '"') {
                entreGuillemets = true;
            } else if (c == separateur) {
                ligne.add(cellule.toString());
                cellule.setLength(0);
            } else if (c == '\n') {
                ligne.add(cellule.toString());
                cellule.setLength(0);
                lignes.add(ligne);
                ligne = new ArrayList<>();
            } else if (c != '\r') {
                cellule.append(c);
            }
        }
        if (cellule.length() > 0 || !ligne.isEmpty()) {
            ligne.add(cellule.toString());
            lignes.add(ligne);
        }
        return lignes;
    }

    /** Le séparateur est celui qui découpe le plus l'en-tête : point-virgule, virgule ou tabulation. */
    private static char separateur(String texte) {
        String entete = texte.lines().findFirst().orElse("");
        char meilleur = ';';
        long maximum = 0;
        for (char candidat : new char[]{';', ',', '\t'}) {
            long n = entete.chars().filter(c -> c == candidat).count();
            if (n > maximum) {
                maximum = n;
                meilleur = candidat;
            }
        }
        return meilleur;
    }

    // ---------------------------------------------------------------------- EXCEL ----

    private static List<List<String>> lireExcel(byte[] contenu) {
        try (Workbook classeur = new XSSFWorkbook(new ByteArrayInputStream(contenu))) {
            Sheet feuille = classeur.getSheetAt(0);
            DataFormatter formateur = new DataFormatter(Locale.FRANCE);
            List<List<String>> lignes = new ArrayList<>();
            for (Row row : feuille) {
                List<String> ligne = new ArrayList<>();
                for (int c = 0; c < row.getLastCellNum(); c++) {
                    ligne.add(formateur.formatCellValue(row.getCell(c)));
                }
                lignes.add(ligne);
            }
            return lignes;
        } catch (IOException | RuntimeException e) {
            throw new IllegalArgumentException("Fichier Excel illisible : " + e.getMessage());
        }
    }
}

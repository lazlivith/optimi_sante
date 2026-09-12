package com.optimisante.backend.domain.partnership.excel;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFDataValidationHelper;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;

/**
 * Produit le classeur vierge de convention de partenariat.
 *
 * <p><b>Le fichier est produit par le serveur, jamais versionné dans le dépôt.</b> Un modèle
 * figé diverge dès que la grille attendue change, et le serveur ingère alors un fichier dont il
 * croit connaître la structure. Ici, l'écriture et la relecture lisent la même description
 * ({@link ModeleDossier}) : elles ne peuvent pas se décaler.</p>
 *
 * <p>Trois dispositions rendent la donnée exploitable au retour :</p>
 *
 * <ul>
 *   <li><b>Tout est verrouillé sauf les cases à remplir.</b> Personne ne déplace une colonne par
 *       mégarde, et la relecture retrouve ses repères. Le verrou n'est pas une sécurité — il se
 *       lève — c'est un garde-fou contre l'accident.</li>
 *   <li><b>Des listes déroulantes</b> là où les valeurs sont contraintes.</li>
 *   <li><b>La version du modèle</b> dans une feuille masquée : un fichier d'une autre version est
 *       refusé avec un message clair, plutôt que lu de travers.</li>
 * </ul>
 */
@Service
public class DossierExcelWriter {

    /**
     * Mot de passe du verrou de feuille.
     *
     * <p>Volontairement public et sans valeur de secret : protéger une feuille Excel n'a jamais
     * empêché personne d'y accéder. Le but est d'éviter la modification accidentelle de la
     * structure, pas d'interdire quoi que ce soit. Le prétendre serait mentir sur la garantie.</p>
     */
    private static final String VERROU = "optimi-structure";

    public byte[] modeleVierge() {
        try (XSSFWorkbook classeur = new XSSFWorkbook();
             ByteArrayOutputStream sortie = new ByteArrayOutputStream()) {

            Styles styles = new Styles(classeur);
            feuilleTechnique(classeur);
            feuilleIdentite(classeur, styles);
            feuilleCapacites(classeur, styles);

            classeur.write(sortie);
            return sortie.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Production du classeur de convention impossible", e);
        }
    }

    // ── Feuilles ────────────────────────────────────────────────────────────────────────

    private void feuilleIdentite(XSSFWorkbook classeur, Styles styles) {
        XSSFSheet f = classeur.createSheet(ModeleDossier.FEUILLE_ETABLISSEMENT);
        f.setColumnWidth(0, 8000);
        f.setColumnWidth(1, 12000);
        f.setColumnWidth(2, 16000);

        Row titre = f.createRow(0);
        cellule(titre, 0, "Convention de partenariat — identité de l'établissement", styles.titre);

        for (ModeleDossier.Champ champ : ModeleDossier.IDENTITE) {
            Row ligne = f.createRow(champ.ligne());
            cellule(ligne, 0, champ.libelle() + (champ.obligatoire() ? " *" : ""), styles.libelle);
            cellule(ligne, 1, "", styles.saisie);
            cellule(ligne, 2, champ.aide(), styles.aide);
        }

        Row note = f.createRow(ModeleDossier.IDENTITE.size() + 2);
        cellule(note, 0, "* Champs obligatoires. Seules les cases jaunes sont modifiables.",
                styles.aide);

        verrouiller(f);
    }

    private void feuilleCapacites(XSSFWorkbook classeur, Styles styles) {
        XSSFSheet f = classeur.createSheet(ModeleDossier.FEUILLE_CAPACITES);

        Row entete = f.createRow(0);
        for (ModeleDossier.Colonne col : ModeleDossier.CAPACITES) {
            cellule(entete, col.index(), col.entete(), styles.libelle);
            f.setColumnWidth(col.index(), 6500);
        }

        // Les lignes de saisie sont créées vides mais stylées : sans cela, l'établissement ne
        // voit pas où écrire, et la protection de feuille empêche d'ajouter des lignes.
        for (int i = 0; i < ModeleDossier.LIGNES_CAPACITES; i++) {
            Row ligne = f.createRow(ModeleDossier.PREMIERE_LIGNE_CAPACITES + i);
            for (ModeleDossier.Colonne col : ModeleDossier.CAPACITES) {
                cellule(ligne, col.index(), "", styles.saisie);
            }
        }

        int derniere = ModeleDossier.PREMIERE_LIGNE_CAPACITES + ModeleDossier.LIGNES_CAPACITES - 1;
        listeDeroulante(f, "specialites", 0, ModeleDossier.PREMIERE_LIGNE_CAPACITES, derniere);
        listeDeroulante(f, "typesAccueil", 1, ModeleDossier.PREMIERE_LIGNE_CAPACITES, derniere);

        verrouiller(f);
    }

    /**
     * Feuille masquée : sources des listes déroulantes, et version du modèle.
     *
     * <p>Masquée plutôt qu'absente : les listes ont besoin d'une plage nommée, et la version doit
     * voyager avec le fichier. Un utilisateur qui la démasque ne casse rien — la relecture ne
     * fait que la lire.</p>
     */
    private void feuilleTechnique(XSSFWorkbook classeur) {
        XSSFSheet f = classeur.createSheet(ModeleDossier.FEUILLE_TECHNIQUE);

        Row versionLigne = f.createRow(0);
        versionLigne.createCell(0).setCellValue("version_modele");
        versionLigne.createCell(1).setCellValue(ModeleDossier.VERSION);

        ecrireColonne(f, 3, ModeleDossier.SPECIALITES);
        ecrireColonne(f, 4, ModeleDossier.TYPES_ACCUEIL);

        nommerPlage(classeur, "specialites", 3, ModeleDossier.SPECIALITES.size());
        nommerPlage(classeur, "typesAccueil", 4, ModeleDossier.TYPES_ACCUEIL.size());

        classeur.setSheetHidden(classeur.getSheetIndex(f), true);
    }

    // ── Outils ──────────────────────────────────────────────────────────────────────────

    private void ecrireColonne(XSSFSheet f, int colonne, java.util.List<String> valeurs) {
        for (int i = 0; i < valeurs.size(); i++) {
            Row ligne = f.getRow(i) != null ? f.getRow(i) : f.createRow(i);
            ligne.createCell(colonne).setCellValue(valeurs.get(i));
        }
    }

    private void nommerPlage(XSSFWorkbook classeur, String nom, int colonne, int hauteur) {
        var plage = classeur.createName();
        plage.setNameName(nom);
        char lettre = (char) ('A' + colonne);
        plage.setRefersToFormula(
                "'" + ModeleDossier.FEUILLE_TECHNIQUE + "'!$" + lettre + "$1:$" + lettre + "$" + hauteur);
    }

    private void listeDeroulante(XSSFSheet f, String plageNommee, int colonne, int de, int a) {
        DataValidationHelper aide = new XSSFDataValidationHelper(f);
        DataValidationConstraint contrainte = aide.createFormulaListConstraint(plageNommee);
        DataValidation validation = aide.createValidation(
                contrainte, new CellRangeAddressList(de, a, colonne, colonne));
        // Avertissement et non blocage : une spécialité absente de notre liste ne doit pas
        // empêcher un établissement de la déclarer. C'est la relecture qui tranche, avec un
        // message que l'on peut lire — un refus muet d'Excel, non.
        validation.setShowErrorBox(true);
        validation.setSuppressDropDownArrow(true);
        f.addValidationData(validation);
    }

    /** Verrouille la feuille en laissant modifiables les seules cellules de saisie. */
    private void verrouiller(XSSFSheet f) {
        f.protectSheet(VERROU);
        f.enableLocking();
    }

    private void cellule(Row ligne, int colonne, String valeur, CellStyle style) {
        Cell c = ligne.createCell(colonne);
        c.setCellValue(valeur);
        c.setCellStyle(style);
    }

    /** Styles du classeur : le verrou est porté par le style, cellule par cellule. */
    private static final class Styles {
        final CellStyle titre;
        final CellStyle libelle;
        final CellStyle saisie;
        final CellStyle aide;

        Styles(XSSFWorkbook classeur) {
            Font gras = classeur.createFont();
            gras.setBold(true);

            titre = classeur.createCellStyle();
            titre.setFont(gras);
            titre.setLocked(true);

            libelle = classeur.createCellStyle();
            libelle.setFont(gras);
            libelle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            libelle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            libelle.setLocked(true);

            // Déverrouillé : c'est ce qui rend la case saisissable malgré la protection de
            // feuille. Le jaune est là pour que cela se voie sans lire la consigne.
            saisie = classeur.createCellStyle();
            saisie.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
            saisie.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            saisie.setBorderBottom(BorderStyle.THIN);
            saisie.setBorderTop(BorderStyle.THIN);
            saisie.setBorderLeft(BorderStyle.THIN);
            saisie.setBorderRight(BorderStyle.THIN);
            saisie.setLocked(false);

            Font discret = classeur.createFont();
            discret.setItalic(true);
            discret.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
            aide = classeur.createCellStyle();
            aide.setFont(discret);
            aide.setLocked(true);
        }
    }

    /** Rend une plage utilisable ailleurs si besoin (non utilisé pour l'instant). */
    static CellRangeAddress plage(int de, int a, int colonne) {
        return new CellRangeAddress(de, a, colonne, colonne);
    }
}

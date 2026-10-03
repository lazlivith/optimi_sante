package com.optimisante.backend.domain.partnership.excel;

import com.optimisante.backend.infrastructure.legal.CompanyIdentity;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;

/**
 * Produit la convention cadre de partenariat, en classeur.
 *
 * <p><b>Pourquoi un classeur et non un PDF.</b> Le modèle était servi en PDF : le centre devait
 * l'imprimer pour le remplir à la main, ou le reconstituer dans son traitement de texte. Les
 * dossiers revenaient manuscrits, parfois illisibles, et l'IBAN recopié à la main est exactement
 * le champ qu'on ne veut pas deviner. Ici, les cases se saisissent au clavier, et l'impression
 * reste possible pour la signature.</p>
 *
 * <p><b>Ce qui est verrouillé, et ce qui ne l'est pas.</b> Le texte de la convention est
 * protégé ; seules les cases jaunes de l'onglet de saisie s'écrivent. Le verrou n'est pas une
 * sécurité — il se lève en deux clics — c'est un garde-fou contre la modification accidentelle
 * d'une clause, qui ferait signer deux textes différents aux deux Parties.</p>
 *
 * <p><b>L'identité d'Optimi Santé vient de la configuration</b>, comme sur toutes les pièces
 * émises. Une valeur absente s'imprime « à compléter » plutôt qu'en blanc : une convention
 * amputée se remarque moins qu'une convention qui annonce ce qui lui manque.</p>
 */
@Service
@RequiredArgsConstructor
public class ConventionExcelWriter {

    /** Même parti pris que le classeur de dossier : empêcher l'accident, pas interdire. */
    private static final String VERROU = "optimi-convention";

    /** Largeur de la colonne de texte, en 1/256e de caractère. */
    private static final int LARGEUR_TEXTE = 110 * 256;

    private static final byte[] BRAND_SOMBRE = {0x0B, 0x24, 0x30};
    private static final byte[] BRAND = {0x11, 0x70, (byte) 0x98};
    private static final byte[] BRAND_CLAIR = {(byte) 0xE3, (byte) 0xEE, (byte) 0xF2};
    private static final byte[] JAUNE_SAISIE = {(byte) 0xFF, (byte) 0xF4, (byte) 0xC2};

    private final CompanyIdentity societe;

    public byte[] modeleVierge() {
        try (XSSFWorkbook classeur = new XSSFWorkbook();
             ByteArrayOutputStream sortie = new ByteArrayOutputStream()) {

            Styles styles = new Styles(classeur);
            feuilleConvention(classeur, styles);
            feuilleSaisie(classeur, styles);
            feuilleTechnique(classeur);

            classeur.write(sortie);
            return sortie.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Production de la convention impossible", e);
        }
    }

    // ── Feuille 1 : le texte de la convention ───────────────────────────────────────────

    private void feuilleConvention(XSSFWorkbook classeur, Styles styles) {
        XSSFSheet f = classeur.createSheet(ModeleConvention.FEUILLE_CONVENTION);
        f.setColumnWidth(0, LARGEUR_TEXTE);
        preparerImpression(f);

        Curseur c = new Curseur(f);

        c.bandeau("CONVENTION CADRE DE PARTENARIAT ET D'ACCUEIL EN FORMATION", styles.banniere, 42);
        c.ligne("Optimi Santé · Établissements et centres de formation partenaires",
                styles.sousTitre, 22);
        c.vide();

        c.ligne("Comment utiliser ce document", styles.sectionTitre, 20);
        for (String etape : ModeleConvention.MODE_EMPLOI) {
            c.paragraphe(etape, styles.corps);
        }
        c.vide();

        c.ligne("Entre les soussignés", styles.sectionTitre, 20);
        c.paragraphe(identiteOptimi(), styles.corps);
        c.ligne("Ci-après désignée « Optimi Santé », d'une part,", styles.corpsItalique, 16);
        c.vide();
        c.paragraphe(
                "L'établissement ou centre de formation dont l'identité est portée à l'onglet "
                + "« " + ModeleConvention.FEUILLE_SAISIE + " » de ce document, représenté par la "
                + "personne qui y est désignée et dûment habilitée à l'engager,", styles.corps);
        c.ligne("Ci-après désigné « le Partenaire », d'autre part,", styles.corpsItalique, 16);
        c.ligne("Ensemble désignés « les Parties ».", styles.corpsItalique, 16);
        c.vide();

        for (ModeleConvention.Article article : ModeleConvention.ARTICLES) {
            c.ligne(article.titre(), styles.sectionTitre, 20);
            for (String alinea : article.alineas()) {
                c.paragraphe(alinea, styles.corps);
            }
            c.vide();
        }

        c.ligne("Signatures", styles.sectionTitre, 20);
        c.paragraphe(
                "Fait en deux (2) exemplaires originaux. Les mentions de lieu, de date et de "
                + "signataire se renseignent à l'onglet « " + ModeleConvention.FEUILLE_SAISIE
                + " ». Chaque Partie signe et appose son cachet sur l'exemplaire imprimé.",
                styles.corps);
        c.vide();
        c.paragraphe("Pour Optimi Santé — " + societe.denominationAffichee()
                + " · Le Président · Signature et cachet", styles.corps);
        c.paragraphe("Pour le Partenaire — Nom, qualité, signature et cachet de l'établissement",
                styles.corps);
        c.vide();
        c.ligne("Modèle version " + ModeleConvention.VERSION
                + " — document non modifiable hors des cases prévues.", styles.mention, 16);

        verrouiller(f);
    }

    /**
     * L'identité de l'éditeur, telle qu'elle doit figurer au contrat.
     *
     * <p>Le nom commercial est distingué de la dénomination sociale : une convention conclue
     * avec « Optimi Santé SAS » — qui n'est pas une personne morale immatriculée — serait
     * attaquable. C'est la société qui s'engage, sous le nom qu'elle exploite.</p>
     */
    private String identiteOptimi() {
        return "La société %s, %s%s, dont le siège social est situé %s, immatriculée sous le "
                .formatted(
                        societe.denominationAffichee(),
                        valeurOuManquant(societe.formeJuridique()),
                        societe.capitalSocial() == null || societe.capitalSocial().isBlank()
                                ? "" : " au capital de " + societe.capitalSocial(),
                        societe.adresseAffichee())
                + "SIRET %s (%s), exploitant la plateforme sous le nom commercial « Optimi Santé », "
                        .formatted(valeurOuManquant(societe.siret()), valeurOuManquant(societe.rcs()))
                + "représentée par son Président en exercice.";
    }

    private static String valeurOuManquant(String valeur) {
        return valeur == null || valeur.isBlank() ? CompanyIdentity.MANQUANT : valeur.trim();
    }

    // ── Feuille 2 : ce que le centre remplit ────────────────────────────────────────────

    private void feuilleSaisie(XSSFWorkbook classeur, Styles styles) {
        XSSFSheet f = classeur.createSheet(ModeleConvention.FEUILLE_SAISIE);
        f.setColumnWidth(0, 48 * 256);
        f.setColumnWidth(1, 42 * 256);
        f.setColumnWidth(2, 60 * 256);
        preparerImpression(f);

        Curseur c = new Curseur(f);

        c.bandeau("Informations à compléter par l'établissement", styles.banniere, 32);
        c.ligne("Seules les cases jaunes sont modifiables. Les champs marqués d'une étoile sont "
                + "exigés.", styles.corpsItalique, 18);
        c.vide();

        for (ModeleConvention.Bloc bloc : ModeleConvention.BLOCS) {
            Row entete = c.nouvelleLigne(20);
            cellule(entete, 0, bloc.titre(), styles.sectionFond);
            cellule(entete, 1, "", styles.sectionFond);
            cellule(entete, 2, "", styles.sectionFond);

            for (ModeleConvention.Champ champ : bloc.champs()) {
                Row ligne = c.nouvelleLigne(18);
                cellule(ligne, 0, champ.libelle() + (champ.obligatoire() ? " *" : ""),
                        styles.libelle);
                cellule(ligne, 1, "", styles.saisie);
                cellule(ligne, 2, champ.aide(), styles.aide);
            }
            c.vide();
        }

        c.paragraphe("Une fois ce document complété, imprimez-le, signez-le et déposez-le sur la "
                + "page « Devenir centre partenaire ». Votre Espace Centre est ouvert après "
                + "validation du dossier.", styles.corps);

        f.createFreezePane(0, 3);
        verrouiller(f);
    }

    // ── Feuille masquée : la version du modèle ──────────────────────────────────────────

    private void feuilleTechnique(XSSFWorkbook classeur) {
        XSSFSheet f = classeur.createSheet(ModeleConvention.FEUILLE_TECHNIQUE);
        Row ligne = f.createRow(0);
        ligne.createCell(0).setCellValue("version_modele");
        ligne.createCell(1).setCellValue(ModeleConvention.VERSION);
        classeur.setSheetHidden(classeur.getSheetIndex(f), true);
    }

    // ── Outils ──────────────────────────────────────────────────────────────────────────

    /**
     * Le classeur sera imprimé pour être signé : sans ces réglages, une clause se coupe en
     * deux pages et l'exemplaire signé n'est pas lisible.
     */
    private void preparerImpression(XSSFSheet f) {
        f.setFitToPage(true);
        f.getPrintSetup().setPaperSize(PrintSetup.A4_PAPERSIZE);
        f.getPrintSetup().setFitWidth((short) 1);
        f.getPrintSetup().setFitHeight((short) 0);
        f.setMargin(PageMargin.LEFT, 0.6);
        f.setMargin(PageMargin.RIGHT, 0.6);
        f.setAutobreaks(true);
    }

    private void verrouiller(XSSFSheet f) {
        f.protectSheet(VERROU);
        f.enableLocking();
    }

    private static void cellule(Row ligne, int colonne, String valeur, CellStyle style) {
        Cell c = ligne.createCell(colonne);
        c.setCellValue(valeur);
        c.setCellStyle(style);
    }

    /** Écrit de haut en bas sans qu'aucun numéro de ligne n'apparaisse dans le code. */
    private static final class Curseur {
        private final XSSFSheet feuille;
        private int n = 0;

        Curseur(XSSFSheet feuille) { this.feuille = feuille; }

        Row nouvelleLigne(int hauteurPoints) {
            Row r = feuille.createRow(n++);
            r.setHeightInPoints(hauteurPoints);
            return r;
        }

        void ligne(String texte, CellStyle style, int hauteurPoints) {
            cellule(nouvelleLigne(hauteurPoints), 0, texte, style);
        }

        /** Une bannière fusionnée sur les trois colonnes, pour qu'elle tienne la largeur. */
        void bandeau(String texte, CellStyle style, int hauteurPoints) {
            int indice = n;
            Row r = nouvelleLigne(hauteurPoints);
            cellule(r, 0, texte, style);
            cellule(r, 1, "", style);
            cellule(r, 2, "", style);
            feuille.addMergedRegion(new CellRangeAddress(indice, indice, 0, 2));
        }

        /**
         * Un alinéa, dont la hauteur suit sa longueur.
         *
         * <p>Excel ne recalcule pas la hauteur d'une ligne au renvoi automatique quand elle est
         * fixée par le code, et ne la recalcule pas non plus à l'ouverture : sans cette
         * estimation, un article de huit lignes s'affiche sur une seule, tronqué.</p>
         */
        void paragraphe(String texte, CellStyle style) {
            int lignes = Math.max(1, (int) Math.ceil(texte.length() / 105.0));
            ligne(texte, style, lignes * 14 + 4);
        }

        void vide() { nouvelleLigne(8); }
    }

    /** Styles du classeur. Le verrou est porté par le style, cellule par cellule. */
    private static final class Styles {
        final CellStyle banniere;
        final CellStyle sousTitre;
        final CellStyle sectionTitre;
        final CellStyle sectionFond;
        final CellStyle corps;
        final CellStyle corpsItalique;
        final CellStyle libelle;
        final CellStyle saisie;
        final CellStyle aide;
        final CellStyle mention;

        Styles(XSSFWorkbook classeur) {
            banniere = classeur.createCellStyle();
            banniere.setFont(police(classeur, 15, true, new byte[]{(byte) 0xFF, (byte) 0xFF, (byte) 0xFF}));
            remplir(banniere, BRAND_SOMBRE);
            banniere.setAlignment(HorizontalAlignment.LEFT);
            banniere.setVerticalAlignment(VerticalAlignment.CENTER);
            banniere.setLocked(true);

            sousTitre = classeur.createCellStyle();
            sousTitre.setFont(police(classeur, 11, false, BRAND));
            sousTitre.setVerticalAlignment(VerticalAlignment.CENTER);
            sousTitre.setLocked(true);

            sectionTitre = classeur.createCellStyle();
            sectionTitre.setFont(police(classeur, 12, true, BRAND_SOMBRE));
            sectionTitre.setVerticalAlignment(VerticalAlignment.CENTER);
            sectionTitre.setBorderBottom(BorderStyle.THIN);
            sectionTitre.setBottomBorderColor(IndexedColors.GREY_40_PERCENT.getIndex());
            sectionTitre.setLocked(true);

            sectionFond = classeur.createCellStyle();
            sectionFond.setFont(police(classeur, 11, true, BRAND_SOMBRE));
            remplir(sectionFond, BRAND_CLAIR);
            sectionFond.setVerticalAlignment(VerticalAlignment.CENTER);
            sectionFond.setLocked(true);

            corps = classeur.createCellStyle();
            corps.setFont(police(classeur, 10, false, null));
            corps.setWrapText(true);
            corps.setVerticalAlignment(VerticalAlignment.TOP);
            corps.setLocked(true);

            corpsItalique = classeur.createCellStyle();
            Font italique = police(classeur, 10, false, null);
            italique.setItalic(true);
            corpsItalique.setFont(italique);
            corpsItalique.setWrapText(true);
            corpsItalique.setVerticalAlignment(VerticalAlignment.TOP);
            corpsItalique.setLocked(true);

            libelle = classeur.createCellStyle();
            libelle.setFont(police(classeur, 10, true, null));
            libelle.setVerticalAlignment(VerticalAlignment.CENTER);
            libelle.setLocked(true);

            // Déverrouillé : c'est ce qui rend la case saisissable malgré la protection de
            // feuille. Le jaune est là pour que cela se voie sans lire la consigne.
            saisie = classeur.createCellStyle();
            remplir(saisie, JAUNE_SAISIE);
            saisie.setBorderBottom(BorderStyle.THIN);
            saisie.setBorderTop(BorderStyle.THIN);
            saisie.setBorderLeft(BorderStyle.THIN);
            saisie.setBorderRight(BorderStyle.THIN);
            saisie.setVerticalAlignment(VerticalAlignment.CENTER);
            saisie.setLocked(false);

            Font discret = police(classeur, 9, false, null);
            discret.setItalic(true);
            discret.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
            aide = classeur.createCellStyle();
            aide.setFont(discret);
            aide.setWrapText(true);
            aide.setVerticalAlignment(VerticalAlignment.CENTER);
            aide.setLocked(true);

            mention = classeur.createCellStyle();
            mention.setFont(discret);
            mention.setLocked(true);
        }

        private static Font police(XSSFWorkbook classeur, int taille, boolean gras, byte[] rvb) {
            var f = classeur.createFont();
            f.setFontName("Calibri");
            f.setFontHeightInPoints((short) taille);
            f.setBold(gras);
            if (rvb != null) f.setColor(new XSSFColor(rvb, null));
            return f;
        }

        private static void remplir(CellStyle style, byte[] rvb) {
            ((org.apache.poi.xssf.usermodel.XSSFCellStyle) style)
                    .setFillForegroundColor(new XSSFColor(rvb, null));
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        }
    }
}

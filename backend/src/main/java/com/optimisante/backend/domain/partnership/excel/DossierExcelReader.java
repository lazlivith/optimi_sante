package com.optimisante.backend.domain.partnership.excel;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Relit le classeur rempli et le valide cellule par cellule.
 *
 * <p><b>C'est ce service qui justifie Excel.</b> Sans relecture, un classeur n'apporte rien de
 * plus qu'un PDF : un fichier opaque qu'un humain devra ressaisir. Ici, la donnée revient
 * structurée, contrôlée, et prête à alimenter la convention.</p>
 *
 * <p>La validation <b>ne s'arrête pas à la première faute</b>. S'arrêter obligerait
 * l'établissement à un aller-retour par erreur ; on rend la liste complète, chaque anomalie
 * désignant sa case.</p>
 */
@Slf4j
@Service
public class DossierExcelReader {

    private static final Pattern COURRIEL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[A-Za-z]{2,}$");
    private static final Pattern FINESS = Pattern.compile("^\\d{9}$");
    private static final DateTimeFormatter JOUR = DateTimeFormatter.ofPattern("dd/MM/uuuu");

    /** Résultat d'une relecture : ce qui a été compris, et ce qui cloche. */
    public record Lecture(
            Map<String, String> identite,
            List<Map<String, String>> capacites,
            List<AnomalieCellule> anomalies
    ) {
        public boolean conforme() {
            return anomalies.isEmpty();
        }
    }

    public Lecture lire(MultipartFile fichier) {
        List<AnomalieCellule> anomalies = new ArrayList<>();

        if (fichier == null || fichier.isEmpty()) {
            anomalies.add(new AnomalieCellule("—", 0, "Fichier", "",
                    "Aucun fichier n'a été reçu."));
            return new Lecture(Map.of(), List.of(), anomalies);
        }

        try (InputStream flux = fichier.getInputStream();
             Workbook classeur = WorkbookFactory.create(flux)) {

            // La version d'abord : si la grille n'est pas celle qu'on croit, tout le reste des
            // contrôles porterait sur les mauvaises cases et produirait un rapport trompeur.
            AnomalieCellule versionInvalide = verifierVersion(classeur);
            if (versionInvalide != null) {
                return new Lecture(Map.of(), List.of(), List.of(versionInvalide));
            }

            Map<String, String> identite = lireIdentite(classeur, anomalies);
            List<Map<String, String>> capacites = lireCapacites(classeur, anomalies);
            return new Lecture(identite, capacites, anomalies);

        } catch (Exception e) {
            log.warn("Classeur de convention illisible : {}", e.getMessage());
            anomalies.add(new AnomalieCellule("—", 0, "Fichier", fichier.getOriginalFilename(),
                    "Ce fichier n'a pas pu être ouvert comme un classeur Excel (.xlsx). "
                    + "Vérifiez que vous envoyez bien le modèle téléchargé, sans l'avoir "
                    + "converti dans un autre format."));
            return new Lecture(Map.of(), List.of(), anomalies);
        }
    }

    // ── Contrôles ───────────────────────────────────────────────────────────────────────

    private AnomalieCellule verifierVersion(Workbook classeur) {
        Sheet technique = classeur.getSheet(ModeleDossier.FEUILLE_TECHNIQUE);
        String version = technique == null ? null : texte(cellule(technique, 0, 1));

        if (version == null || version.isBlank()) {
            return new AnomalieCellule(ModeleDossier.FEUILLE_TECHNIQUE, 1, "Version du modèle", "",
                    "Ce classeur ne porte pas la marque du modèle Optimi Santé. Repartez du "
                    + "modèle à télécharger sur cette page.");
        }
        if (!ModeleDossier.VERSION.equals(version)) {
            return new AnomalieCellule(ModeleDossier.FEUILLE_TECHNIQUE, 1, "Version du modèle", version,
                    "Ce classeur suit une version antérieure du modèle (attendue : "
                    + ModeleDossier.VERSION + "). Les colonnes ont changé depuis : téléchargez "
                    + "le modèle à jour et reportez-y vos informations.");
        }
        return null;
    }

    private Map<String, String> lireIdentite(Workbook classeur, List<AnomalieCellule> anomalies) {
        Map<String, String> valeurs = new LinkedHashMap<>();
        Sheet f = classeur.getSheet(ModeleDossier.FEUILLE_ETABLISSEMENT);
        if (f == null) {
            anomalies.add(new AnomalieCellule(ModeleDossier.FEUILLE_ETABLISSEMENT, 0, "Feuille", "",
                    "La feuille « " + ModeleDossier.FEUILLE_ETABLISSEMENT + " » est absente."));
            return valeurs;
        }

        for (ModeleDossier.Champ champ : ModeleDossier.IDENTITE) {
            String valeur = texte(cellule(f, champ.ligne(), 1));
            valeurs.put(champ.cle(), valeur);
            int ligneAffichee = champ.ligne() + 1;

            if (valeur.isBlank()) {
                if (champ.obligatoire()) {
                    anomalies.add(new AnomalieCellule(ModeleDossier.FEUILLE_ETABLISSEMENT,
                            ligneAffichee, champ.libelle(), "", "cette information est obligatoire."));
                }
                continue;
            }
            if ("contactEmail".equals(champ.cle()) && !COURRIEL.matcher(valeur).matches()) {
                anomalies.add(new AnomalieCellule(ModeleDossier.FEUILLE_ETABLISSEMENT,
                        ligneAffichee, champ.libelle(), valeur,
                        "cette adresse e-mail n'est pas valide."));
            }
            if ("finessAccreditation".equals(champ.cle())
                    && !FINESS.matcher(valeur.replace(" ", "")).matches()) {
                anomalies.add(new AnomalieCellule(ModeleDossier.FEUILLE_ETABLISSEMENT,
                        ligneAffichee, champ.libelle(), valeur,
                        "un numéro FINESS compte 9 chiffres. Laissez la case vide si votre "
                        + "établissement n'en a pas."));
            }
        }
        return valeurs;
    }

    private List<Map<String, String>> lireCapacites(Workbook classeur,
                                                    List<AnomalieCellule> anomalies) {
        List<Map<String, String>> lignes = new ArrayList<>();
        Sheet f = classeur.getSheet(ModeleDossier.FEUILLE_CAPACITES);
        if (f == null) {
            anomalies.add(new AnomalieCellule(ModeleDossier.FEUILLE_CAPACITES, 0, "Feuille", "",
                    "La feuille « " + ModeleDossier.FEUILLE_CAPACITES + " » est absente."));
            return lignes;
        }

        for (int i = ModeleDossier.PREMIERE_LIGNE_CAPACITES; i <= f.getLastRowNum(); i++) {
            Row ligne = f.getRow(i);
            if (ligne == null) continue;

            Map<String, String> valeurs = new LinkedHashMap<>();
            for (ModeleDossier.Colonne col : ModeleDossier.CAPACITES) {
                valeurs.put(col.cle(), texte(ligne.getCell(col.index())));
            }
            // Une ligne entièrement vide n'est pas une faute : le modèle en propose trente, et
            // personne n'est tenu de toutes les remplir.
            if (valeurs.values().stream().allMatch(String::isBlank)) continue;

            int ligneAffichee = i + 1;
            controlerLigneCapacite(valeurs, ligneAffichee, anomalies);
            lignes.add(valeurs);
        }

        if (lignes.isEmpty()) {
            anomalies.add(new AnomalieCellule(ModeleDossier.FEUILLE_CAPACITES,
                    ModeleDossier.PREMIERE_LIGNE_CAPACITES + 1, "Spécialité", "",
                    "déclarez au moins une capacité d'accueil : c'est l'objet de la convention."));
        }
        return lignes;
    }

    private void controlerLigneCapacite(Map<String, String> valeurs, int ligneAffichee,
                                        List<AnomalieCellule> anomalies) {
        String feuille = ModeleDossier.FEUILLE_CAPACITES;

        for (ModeleDossier.Colonne col : ModeleDossier.CAPACITES) {
            if (col.obligatoire() && valeurs.get(col.cle()).isBlank()) {
                anomalies.add(new AnomalieCellule(feuille, ligneAffichee, col.entete(), "",
                        "cette colonne doit être renseignée dès que la ligne est commencée."));
            }
        }

        String type = valeurs.get("typeAccueil");
        if (!type.isBlank() && !ModeleDossier.TYPES_ACCUEIL.contains(type)) {
            anomalies.add(new AnomalieCellule(feuille, ligneAffichee, "Type d'accueil", type,
                    "valeur non reconnue. Choisissez dans la liste déroulante : "
                    + String.join(", ", ModeleDossier.TYPES_ACCUEIL) + "."));
        }

        String places = valeurs.get("places");
        if (!places.isBlank()) {
            try {
                int n = Integer.parseInt(places.trim());
                if (n <= 0) {
                    anomalies.add(new AnomalieCellule(feuille, ligneAffichee, "Places par session",
                            places, "le nombre de places doit être supérieur à zéro."));
                }
            } catch (NumberFormatException e) {
                anomalies.add(new AnomalieCellule(feuille, ligneAffichee, "Places par session",
                        places, "un nombre entier est attendu."));
            }
        }

        LocalDate debut = date(valeurs.get("debut"), feuille, ligneAffichee, "Début (JJ/MM/AAAA)", anomalies);
        LocalDate fin = date(valeurs.get("fin"), feuille, ligneAffichee, "Fin (JJ/MM/AAAA)", anomalies);
        if (debut != null && fin != null && !fin.isAfter(debut)) {
            anomalies.add(new AnomalieCellule(feuille, ligneAffichee, "Fin (JJ/MM/AAAA)",
                    valeurs.get("fin"), "la fin doit venir après le début ("
                    + valeurs.get("debut") + ")."));
        }
    }

    private LocalDate date(String valeur, String feuille, int ligne, String colonne,
                           List<AnomalieCellule> anomalies) {
        if (valeur.isBlank()) return null;
        try {
            // STRICT : sans cela, « 32/13/2026 » serait silencieusement ramené à une date
            // valide, et la convention porterait une période que personne n'a saisie.
            return LocalDate.parse(valeur.trim(), JOUR.withResolverStyle(
                    java.time.format.ResolverStyle.STRICT));
        } catch (DateTimeParseException e) {
            anomalies.add(new AnomalieCellule(feuille, ligne, colonne, valeur,
                    "date attendue au format JJ/MM/AAAA (exemple : 01/09/2026)."));
            return null;
        }
    }

    // ── Lecture brute ───────────────────────────────────────────────────────────────────

    private Cell cellule(Sheet f, int ligne, int colonne) {
        Row r = f.getRow(ligne);
        return r == null ? null : r.getCell(colonne);
    }

    /**
     * Contenu d'une cellule, en texte.
     *
     * <p>Une date saisie dans une case formatée « date » n'arrive pas comme une chaîne : Excel la
     * stocke en nombre. La reformater ici évite de rejeter une saisie parfaitement correcte au
     * motif qu'elle vaut « 46266 ».</p>
     */
    private String texte(Cell c) {
        if (c == null) return "";
        return switch (c.getCellType()) {
            case STRING -> c.getStringCellValue().trim();
            case BOOLEAN -> String.valueOf(c.getBooleanCellValue());
            case NUMERIC -> DateUtil.isCellDateFormatted(c)
                    ? c.getDateCellValue().toInstant().atZone(ZoneId.systemDefault())
                        .toLocalDate().format(JOUR)
                    // Un entier saisi dans une case générique revient en 12.0 : on retire le
                    // zéro décimal, sans quoi « 12 places » deviendrait un nombre invalide.
                    : nombreLisible(c.getNumericCellValue());
            case FORMULA -> {
                try {
                    yield c.getStringCellValue().trim();
                } catch (IllegalStateException e) {
                    yield nombreLisible(c.getNumericCellValue());
                }
            }
            default -> "";
        };
    }

    private String nombreLisible(double valeur) {
        return valeur == Math.rint(valeur)
                ? String.valueOf((long) valeur)
                : String.valueOf(valeur);
    }
}

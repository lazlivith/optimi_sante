package com.optimisante.backend.domain.partnership.excel;

import java.util.List;

/**
 * Description du classeur de convention : ce qu'il contient, et où.
 *
 * <p><b>Une seule description, partagée par l'écriture et la relecture.</b> C'est le point
 * central de ce lot : si le service qui produit le modèle et celui qui le relit décrivaient
 * chacun sa version de la grille, ils divergeraient au premier ajout de colonne — et le serveur
 * lirait alors l'adresse là où l'établissement a saisi le téléphone, sans que rien ne le
 * signale. C'est exactement le défaut qui laissait six champs vides sur le reçu.</p>
 */
public final class ModeleDossier {

    private ModeleDossier() {}

    /**
     * Version du modèle, inscrite dans une cellule masquée du classeur.
     *
     * <p>À incrémenter dès que la grille change. Un fichier rempli sur une version antérieure
     * est refusé avec un message clair plutôt qu'ingéré de travers : les colonnes auraient
     * glissé, et les données seraient lues dans les mauvaises cases.</p>
     */
    public static final String VERSION = "1";

    public static final String FEUILLE_ETABLISSEMENT = "Établissement";
    public static final String FEUILLE_CAPACITES = "Capacités d'accueil";
    /** Feuille masquée : sert de support aux listes déroulantes et porte la version. */
    public static final String FEUILLE_TECHNIQUE = "_modele";

    /** Première ligne de saisie de la grille des capacités (après l'en-tête). */
    public static final int PREMIERE_LIGNE_CAPACITES = 1;
    /** Nombre de lignes de saisie proposées. Au-delà, l'établissement nous écrit. */
    public static final int LIGNES_CAPACITES = 30;

    /** Un champ d'identité : son libellé, sa ligne, et s'il est exigé. */
    public record Champ(String cle, String libelle, int ligne, boolean obligatoire, String aide) {}

    /**
     * Identité de l'établissement.
     *
     * <p>Le libellé est en colonne A, la saisie en colonne B — la seule déverrouillée.</p>
     */
    public static final List<Champ> IDENTITE = List.of(
            new Champ("institutionName",   "Nom de l'établissement",      1, true,
                      "Dénomination officielle, telle qu'elle figurera sur la convention."),
            new Champ("finessAccreditation", "Numéro FINESS",             2, false,
                      "9 chiffres. Laissez vide si votre établissement n'en dispose pas."),
            new Champ("contactPersonName", "Personne à contacter",        3, true,
                      "Prénom et nom du référent du partenariat."),
            new Champ("contactEmail",      "Adresse e-mail",              4, true,
                      "Adresse à laquelle nous répondrons."),
            new Champ("contactPhone",      "Téléphone",                   5, true,
                      "Avec l'indicatif international si hors France."),
            new Champ("address",           "Adresse du siège",            6, true,
                      "Voie, code postal, ville, pays."));

    /** Colonnes de la grille des capacités d'accueil. */
    public record Colonne(String cle, String entete, int index, boolean obligatoire) {}

    public static final List<Colonne> CAPACITES = List.of(
            new Colonne("specialite",   "Spécialité",              0, true),
            new Colonne("typeAccueil",  "Type d'accueil",          1, true),
            new Colonne("places",       "Places par session",      2, true),
            new Colonne("debut",        "Début (JJ/MM/AAAA)",      3, true),
            new Colonne("fin",          "Fin (JJ/MM/AAAA)",        4, true));

    /**
     * Valeurs admises pour « Type d'accueil ».
     *
     * <p>Proposées en liste déroulante : la donnée revient propre, ce qui épargne la moitié des
     * allers-retours avec l'établissement.</p>
     */
    public static final List<String> TYPES_ACCUEIL =
            List.of("Stage d'observation", "Stage clinique", "Formation continue", "Mixte");

    /** Spécialités proposées. « Autre » reste possible : la liste ne prétend pas être close. */
    public static final List<String> SPECIALITES = List.of(
            "Cardiologie", "Chirurgie générale", "Gynécologie-obstétrique", "Imagerie médicale",
            "Médecine interne", "Néonatalogie", "Neurologie", "Ophtalmologie", "Pédiatrie",
            "Réanimation", "Urgences", "Autre");
}

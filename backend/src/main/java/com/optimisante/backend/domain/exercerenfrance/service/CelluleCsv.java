package com.optimisante.backend.domain.exercerenfrance.service;

/**
 * Neutralise une valeur avant de l'écrire dans un fichier CSV.
 *
 * <p><b>L'attaque que cette classe empêche.</b> Un tableur n'ouvre pas un CSV, il l'INTERPRÈTE :
 * une cellule commençant par {@code =}, {@code +}, {@code -} ou {@code @} est une FORMULE. Un
 * visiteur qui inscrit {@code =HYPERLINK("http://…","Cliquez")} dans son nom — ou pire,
 * {@code =cmd|'/c calc'!A1} — fait exécuter cela sur le poste de l'administrateur qui ouvre
 * l'export. C'est une attaque banale sur les exports de formulaires publics, et elle ne
 * demande aucune faille technique : seulement un champ de texte et un export.</p>
 *
 * <p><b>Pourquoi l'apostrophe plutôt que l'échappement.</b> Les guillemets protègent le
 * DÉCOUPAGE du fichier, pas son interprétation : {@code "=1+1"} reste une formule pour Excel.
 * Une apostrophe en tête, elle, force le tableur à traiter la cellule comme du texte. Elle
 * s'affiche dans la barre de formule, mais pas dans la cellule.</p>
 *
 * <p><b>La tabulation et le retour chariot comptent aussi.</b> Certains tableurs les ignorent
 * en tête de cellule avant d'analyser ce qui suit : {@code \t=1+1} redevient une formule.</p>
 */
public final class CelluleCsv {

    /** Les caractères par lesquels une formule peut commencer, directement ou après coup. */
    private static final String AMORCES = "=+-@\t\r\n";

    private CelluleCsv() {
    }

    /** @return la valeur, entourée de guillemets et rendue inoffensive pour un tableur */
    public static String neutraliser(String valeur) {
        if (valeur == null) {
            return "\"\"";
        }
        String v = valeur.replace("\"", "\"\"")
                .replace("\r", " ")
                .replace("\n", " ");
        // L'amorce est cherchée APRÈS les blancs de tête, et non sur le premier caractère.
        // Les retours à la ligne viennent d'être remplacés par des espaces : chercher en
        // position zéro laissait passer « \r=1+1 », devenu « =1+1 » précédé d'une espace —
        // que le tableur ignore avant d'analyser ce qui suit.
        String utile = v.stripLeading();
        if (!utile.isEmpty() && AMORCES.indexOf(utile.charAt(0)) >= 0) {
            v = "'" + v;
        }
        return "\"" + v + "\"";
    }

    /**
     * Un numéro de téléphone, écrit pour qu'on puisse le copier tel quel.
     *
     * <p><b>Pourquoi cette colonne échappe à l'apostrophe.</b> L'apostrophe de protection est
     * invisible quand on la TAPE dans un tableur ; importée depuis un CSV, elle devient un
     * caractère comme un autre et s'affiche dans la cellule. Mesuré sur Excel :
     * {@code '+242 06 123 45 67} s'affiche avec son apostrophe. Or ce fichier existe pour
     * qu'on colle ces numéros dans WhatsApp — un numéro précédé d'une apostrophe ne vaut
     * rien.</p>
     *
     * <p><b>Et pourquoi c'est sans danger ici.</b> Le numéro est contrôlé à la saisie : un
     * {@code +} facultatif, puis des chiffres et des espaces. Aucune formule n'a cette forme.
     * Mesuré également : Excel n'évalue pas {@code +242 06 123 45 67}, parce que ce n'est pas
     * une formule valide — il retombe sur du texte.</p>
     *
     * <p><b>Sauf sans espace.</b> {@code +24206123456} devient un NOMBRE, affiché
     * {@code 2,4206E+10} : le {@code +} est pris pour un signe et les chiffres pour une
     * valeur. Ces numéros-là gardent donc l'apostrophe : une apostrophe gênante vaut mieux
     * qu'un numéro détruit.</p>
     *
     * <p>Toute valeur qui ne ressemble pas à un numéro repasse par {@link #neutraliser} :
     * cette fonction accorde une dispense, elle ne la présume pas.</p>
     */
    public static String numeroTelephone(String valeur) {
        if (valeur == null || valeur.isBlank()) {
            return "\"\"";
        }
        String v = valeur.trim();
        boolean formeDeNumero = v.matches("\\+?[0-9][0-9 ]*");
        boolean porteUnEspace = v.indexOf(' ') > 0;
        return formeDeNumero && porteUnEspace ? "\"" + v + "\"" : neutraliser(valeur);
    }

    /**
     * Le marqueur d'ordre des octets, à écrire en tête de fichier.
     *
     * <p>Sans lui, Excel en configuration française lit l'UTF-8 comme du Windows-1252 : les
     * accents deviennent illisibles, et le premier retour qu'on reçoit porte là-dessus.</p>
     */
    public static final String BOM = "\uFEFF";

    /**
     * Le séparateur attendu par Excel en configuration française.
     *
     * <p>La virgule y découpe mal : un fichier s'ouvre alors en une seule colonne.</p>
     */
    public static final char SEPARATEUR = ';';
}

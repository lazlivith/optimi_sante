package com.optimisante.backend.domain.partnership.excel;

/**
 * Une anomalie située dans le classeur.
 *
 * <p>Située, et c'est tout l'intérêt : « le fichier est invalide » oblige l'établissement à
 * relire trente lignes ; « Capacités d'accueil, ligne 7, colonne Fin : 32/13/2026 n'est pas une
 * date » lui désigne la case. Un rejet global aurait coûté un aller-retour de plus à chaque
 * faute.</p>
 *
 * @param feuille  nom de la feuille, tel qu'il apparaît dans Excel
 * @param ligne    numéro affiché par Excel (1 pour la première), pas l'index interne
 * @param colonne  libellé de la colonne ou du champ, jamais son index
 * @param valeur   ce qui a été saisi, pour que la personne se reconnaisse ; vide si absent
 * @param probleme ce qui ne va pas, et si possible ce qu'on attendait
 */
public record AnomalieCellule(
        String feuille,
        int ligne,
        String colonne,
        String valeur,
        String probleme
) {
    public String resume() {
        return "%s, ligne %d, « %s » : %s".formatted(feuille, ligne, colonne, probleme);
    }
}

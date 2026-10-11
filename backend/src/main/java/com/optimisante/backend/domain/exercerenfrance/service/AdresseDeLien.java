package com.optimisante.backend.domain.exercerenfrance.service;

import java.util.Locale;

/**
 * Ce qu'une adresse de lien officiel a le droit d'être.
 *
 * <p><b>La faille que cette classe ferme.</b> Les liens de la page sont saisis dans
 * l'administration, puis rendus au visiteur dans un {@code <a href>}. Une adresse
 * {@code javascript:…} s'exécute alors au clic, dans la session du visiteur, depuis notre
 * propre domaine : c'est une injection de script, à la différence près qu'elle passe par un
 * champ de formulaire plutôt que par une faille technique. {@code data:text/html,…} fait de
 * même, en servant une page entière que nous aurions l'air d'avoir écrite.</p>
 *
 * <p><b>Pourquoi une liste blanche et non une liste noire.</b> Interdire {@code javascript:}
 * laisserait passer {@code vbscript:}, {@code data:}, les variantes avec espaces insécables,
 * les encodages. N'autoriser que {@code http} et {@code https} ferme la question une fois
 * pour toutes — et aucune source officielle n'a jamais été ailleurs.</p>
 *
 * <p><b>Validé au serveur, et pas seulement à l'écran.</b> Un contrôle dans le navigateur
 * protège l'administrateur de sa propre faute de frappe ; il ne protège de rien si l'appel
 * est fait sans passer par l'écran.</p>
 */
public final class AdresseDeLien {

    /** Valeur de remplacement tant qu'un lien n'est pas renseigné. La page ne l'affiche pas. */
    public static final String A_COMPLETER = "A_COMPLETER";

    private AdresseDeLien() {
    }

    /**
     * @return l'adresse, inchangée
     * @throws IllegalArgumentException si elle n'est ni {@code http}, ni {@code https}, ni le
     *                                  marqueur « à compléter »
     */
    public static String verifier(String url) {
        if (url == null || url.isBlank() || A_COMPLETER.equals(url.trim())) {
            return A_COMPLETER;
        }
        String propre = url.trim();
        // Le contrôle porte sur la chaîne telle qu'elle sera RENDUE, après neutralisation des
        // espaces et des caractères de contrôle qu'un navigateur ignore à l'analyse : sans
        // cela, « java\nscript:… » passerait ici et s'exécuterait là-bas.
        String compacte = propre.replaceAll("[\\s\\u0000-\\u001F\\u007F\\u00A0\\u2000-\\u200F\\uFEFF]", "")
                .toLowerCase(Locale.ROOT);
        if (!compacte.startsWith("http://") && !compacte.startsWith("https://")) {
            throw new IllegalArgumentException(
                    "Une adresse de lien doit commencer par http:// ou https:// (reçu : « "
                    + propre + " »).");
        }
        return propre;
    }
}

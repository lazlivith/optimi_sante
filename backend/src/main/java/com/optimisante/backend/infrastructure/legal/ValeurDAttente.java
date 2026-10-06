package com.optimisante.backend.infrastructure.legal;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Reconnaît une valeur de configuration qu'on a posée « en attendant ».
 *
 * <p><b>Pourquoi ce besoin existe.</b> Le garde de l'identité légale refusait le démarrage
 * quand une mention était <i>vide</i>. Mais personne ne laisse une variable vide : on y met
 * « à renseigner », le temps d'obtenir la vraie valeur. Ce n'est pas vide, le garde laissait
 * donc passer — et le 3 octobre 2026, les reçus de la plateforme annonçaient « SIRET :
 * IMMATRICULATION EN COURS » et « TVA : EN COURS D'ATTRIBUTION » depuis des mois. Nous ne
 * l'avons découvert qu'en lisant un document de production, par hasard.</p>
 *
 * <p><b>Pourquoi ce n'est pas un simple {@code contains}.</b> Chercher « na » dans une chaîne
 * rejetterait « Villena<b>ve</b> d'Ornon ». La comparaison se fait donc sur des <i>mots</i>
 * entiers pour les abréviations courtes, et sur des locutions pour les formules. Une adresse
 * réelle doit passer : c'est la propriété que les tests fixent en premier.</p>
 *
 * <p><b>Ce que cette classe ne fait pas.</b> Elle ne valide pas la <i>justesse</i> d'une valeur.
 * Un SIRET à quatorze chiffres inventé passera : seule une vérification auprès du répertoire
 * officiel le dirait, et ce n'est pas le rôle d'un contrôle au démarrage. Elle attrape
 * l'oubli, pas la fraude.</p>
 */
public final class ValeurDAttente {

    private ValeurDAttente() {}

    /**
     * Locutions d'attente, recherchées telles quelles dans la valeur normalisée.
     *
     * <p>Plusieurs mots : le risque de les croiser par accident dans une dénomination ou une
     * adresse réelle est négligeable.</p>
     */
    private static final List<String> LOCUTIONS = List.of(
            "a renseigner", "a completer", "a definir", "a fournir", "a venir",
            "en cours", "non renseigne", "non communique", "a preciser",
            "sans objet", "to do", "to be defined", "lorem ipsum");

    /**
     * Abréviations d'attente, comparées à des MOTS entiers.
     *
     * <p>En sous-chaîne, « na » rejetterait « Villenave » et « xx » rejetterait un nom propre.
     * Le découpage en mots lève l'ambiguïté.</p>
     */
    private static final Set<String> MOTS = Set.of(
            "todo", "tbd", "na", "n/a", "nc", "xxx", "xxxx", "xx",
            "placeholder", "inconnu", "inconnue", "vide", "neant", "?", "...", "-", "--");

    /** Vrai si la valeur ressemble à ce qu'on écrit en attendant la vraie. */
    public static boolean enEst(String valeur) {
        if (valeur == null || valeur.isBlank()) {
            return false; // L'absence se traite ailleurs : ici on cherche un faux-semblant.
        }
        String normalisee = normaliser(valeur);

        for (String locution : LOCUTIONS) {
            if (normalisee.contains(locution)) {
                return true;
            }
        }
        // La barre oblique n'est PAS un séparateur : sans cela « n/a » serait coupé en deux et
        // ne correspondrait jamais à l'entrée « n/a ». Le tiret non plus, pour que
        // « Villenave-d'Ornon » reste un seul mot et ne croise aucune abréviation.
        for (String mot : normalisee.split("[\\s,;:()\\[\\]]+")) {
            if (!mot.isEmpty() && MOTS.contains(mot)) {
                return true;
            }
        }
        return false;
    }

    /** Minuscules, sans accents : « À RENSEIGNER » et « a renseigner » sont la même chose. */
    private static String normaliser(String valeur) {
        String sansAccent = Normalizer.normalize(valeur.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return sansAccent.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }
}

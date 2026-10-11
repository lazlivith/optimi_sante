package com.optimisante.backend.common.security;

import java.util.Locale;

/**
 * Ce qu'une adresse saisie dans l'administration a le droit d'être, avant d'être rendue dans
 * un {@code <a href>}.
 *
 * <p><b>La faille que cette classe ferme.</b> Une adresse {@code javascript:alert(1)} placée
 * dans un {@code href} s'exécute au clic, dans la session du visiteur et depuis notre propre
 * domaine. C'est une injection de script qui passe par un champ de formulaire plutôt que par
 * une faille technique — et le champ est rempli par un administrateur, ou, pour les
 * rendez-vous, par un partenaire extérieur à la maison.</p>
 *
 * <p><b>Liste blanche, et non liste noire.</b> Interdire {@code javascript:} laisserait
 * {@code vbscript:}, {@code data:text/html}, et les encodages. N'autoriser que {@code http}
 * et {@code https} ferme la question une fois pour toutes.</p>
 *
 * <p><b>Le contrôle porte sur la chaîne COMPACTÉE.</b> Un navigateur ignore espaces, tabulations
 * et caractères de contrôle lorsqu'il analyse une adresse : {@code java\nscript:alert(1)}
 * s'exécute. Vérifier ce qui est écrit, et non ce qui sera interprété, ne protège de rien.</p>
 */
public final class AdresseWeb {

    private AdresseWeb() {
    }

    /** Vrai si l'adresse peut être placée sans danger dans un {@code href}. */
    public static boolean estSure(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        String compacte = url.replaceAll("[\\s\\u0000-\\u001F\\u007F\\u00A0\\u2000-\\u200F\\uFEFF]", "")
                .toLowerCase(Locale.ROOT);
        return compacte.startsWith("http://") || compacte.startsWith("https://");
    }

    /**
     * @return l'adresse nettoyée de ses espaces de bordure
     * @throws IllegalArgumentException si elle n'est ni {@code http} ni {@code https}
     */
    public static String exiger(String url, String champ) {
        if (!estSure(url)) {
            throw new IllegalArgumentException(
                    champ + " : seules les adresses commençant par http:// ou https:// sont "
                    + "acceptées.");
        }
        return url.trim();
    }
}

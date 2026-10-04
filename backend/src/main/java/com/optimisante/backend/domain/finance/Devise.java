package com.optimisante.backend.domain.finance;

import java.util.Locale;
import java.util.Set;

/**
 * Une devise, et le peu qu'il faut savoir d'elle pour manipuler un montant sans se tromper.
 *
 * <p><b>Pourquoi ce n'est pas une simple chaîne.</b> Un code devise en {@code String} ne porte
 * pas le nombre de décimales, et c'est précisément ce que l'on oublie : la plateforme
 * multipliait tout montant par cent pour l'envoyer à Stripe, ce qui est juste pour l'euro et
 * faux pour le franc CFA, qui n'a pas de subdivision. Une devise qui ignore ses propres règles
 * oblige chaque appelant à les connaître — et il suffit qu'un seul les ignore.</p>
 *
 * <p>La devise de référence de la plateforme est l'euro : c'est en euros que les prix sont
 * stockés, et toute autre devise est une devise de <i>présentation</i>, obtenue par conversion.</p>
 */
public record Devise(String code) {

    /** La devise dans laquelle les prix sont stockés et la comptabilité tenue. */
    public static final Devise REFERENCE = new Devise("EUR");

    /**
     * Devises sans subdivision, telles que Stripe les recense.
     *
     * <p>XAF et XOF — les francs CFA — sont celles qui concernent la plateforme.</p>
     */
    private static final Set<String> SANS_DECIMALE = Set.of(
            "BIF", "CLP", "DJF", "GNF", "JPY", "KMF", "KRW", "MGA",
            "PYG", "RWF", "UGX", "VND", "VUV", "XAF", "XOF", "XPF");

    /**
     * Devises à trois décimales.
     *
     * <p>Elles imposent à Stripe un montant multiple de dix dans leur plus petite unité. La
     * plateforme ne les vend pas, et plutôt que d'implémenter à l'aveugle une règle qu'aucun
     * test réel ne couvrirait, elle les refuse — voir {@link Montant#versPlusPetiteUnite()}.</p>
     */
    private static final Set<String> TROIS_DECIMALES = Set.of("BHD", "JOD", "KWD", "OMR", "TND");

    public Devise {
        code = code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
        if (!code.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException(
                    "Code devise invalide : « " + code + " ». Attendu : un code ISO 4217 à "
                    + "trois lettres, par exemple « EUR ».");
        }
    }

    public static Devise de(String code) {
        return new Devise(code);
    }

    /** Nombre de décimales de la devise. */
    public int decimales() {
        if (SANS_DECIMALE.contains(code)) return 0;
        if (TROIS_DECIMALES.contains(code)) return 3;
        return 2;
    }

    /** Vrai si la plateforme sait convertir un montant de cette devise vers Stripe. */
    public boolean gereeParStripe() {
        return !TROIS_DECIMALES.contains(code);
    }

    public boolean estReference() {
        return REFERENCE.code.equals(code);
    }

    /** Le code tel que Stripe l'attend : trois lettres minuscules. */
    public String codeStripe() {
        return code.toLowerCase(Locale.ROOT);
    }

    @Override
    public String toString() {
        return code;
    }
}

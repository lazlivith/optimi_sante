package com.optimisante.backend.domain.orders.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Set;

/**
 * Convertit un montant en la plus petite unité monétaire que Stripe attend.
 *
 * <p><b>Pourquoi cette classe existe.</b> La conversion était écrite en dur à deux endroits,
 * sous la forme {@code montant.multiply(100)}. Elle est juste pour l'euro, et fausse pour toute
 * devise qui ne se divise pas en centièmes. Le franc CFA n'a pas de subdivision : un paiement de
 * 328 000 XAF serait parti chez Stripe pour 32 800 000 XAF, soit <b>cent fois le prix</b>. Le
 * jour où la plateforme encaisse en Afrique centrale, ce calcul doit déjà être correct — pas
 * être découvert sur la première transaction.</p>
 *
 * <p><b>Ce qu'elle refuse de deviner.</b> Les devises à trois décimales (dinar koweïtien,
 * bahreïni, jordanien, omanais, tunisien) imposent à Stripe une contrainte supplémentaire : le
 * montant doit être un multiple de dix dans la plus petite unité. La plateforme ne les vend pas,
 * et plutôt que d'implémenter à l'aveugle une règle qu'aucun test réel ne couvrirait, cette
 * classe lève une erreur explicite. Un refus que l'on lit vaut mieux qu'un arrondi silencieux
 * sur une somme d'argent.</p>
 *
 * @see <a href="https://docs.stripe.com/currencies">Stripe — Supported currencies</a>
 */
public final class MontantStripe {

    /**
     * Devises sans subdivision : le montant se transmet tel quel, sans multiplication.
     *
     * <p>Liste tenue par Stripe. XAF et XOF — francs CFA d'Afrique centrale et de l'Ouest — sont
     * celles qui concernent directement la plateforme.</p>
     */
    private static final Set<String> SANS_DECIMALE = Set.of(
            "bif", "clp", "djf", "gnf", "jpy", "kmf", "krw", "mga",
            "pyg", "rwf", "ugx", "vnd", "vuv", "xaf", "xof", "xpf");

    /** Devises à trois décimales, que cette classe refuse plutôt que de les traiter de travers. */
    private static final Set<String> TROIS_DECIMALES = Set.of("bhd", "jod", "kwd", "omr", "tnd");

    private MontantStripe() {}

    /**
     * Le code devise sous la forme attendue par Stripe : trois lettres minuscules.
     *
     * @throws IllegalArgumentException si le code n'a pas la forme d'un code ISO 4217
     */
    public static String normaliser(String devise) {
        String code = devise == null ? "" : devise.trim().toLowerCase(Locale.ROOT);
        if (!code.matches("[a-z]{3}")) {
            throw new IllegalArgumentException(
                    "Devise Stripe invalide : « " + devise + " ». Attendu : un code ISO 4217 "
                    + "à trois lettres, par exemple « eur ».");
        }
        return code;
    }

    /** Nombre de décimales de la devise, tel que Stripe le définit. */
    public static int decimales(String devise) {
        String code = normaliser(devise);
        if (SANS_DECIMALE.contains(code)) return 0;
        if (TROIS_DECIMALES.contains(code)) return 3;
        return 2;
    }

    /**
     * Le montant, exprimé dans la plus petite unité de la devise.
     *
     * <p>L'arrondi est au plus proche, demi-supérieur — la même règle que celle employée pour la
     * ventilation de la taxe. Elle ne joue que si le montant porte plus de décimales que la
     * devise n'en admet, ce qui ne devrait pas arriver : les prix sont stockés en
     * {@code numeric(10,2)}.</p>
     *
     * @param montant montant positif ou nul, dans l'unité courante de la devise
     * @param devise  code ISO 4217
     * @throws IllegalArgumentException     si le montant est absent ou négatif
     * @throws UnsupportedOperationException si la devise a trois décimales
     */
    public static long versPlusPetiteUnite(BigDecimal montant, String devise) {
        String code = normaliser(devise);
        if (montant == null || montant.signum() < 0) {
            throw new IllegalArgumentException(
                    "Montant Stripe absent ou négatif : " + montant);
        }
        if (TROIS_DECIMALES.contains(code)) {
            throw new UnsupportedOperationException(
                    "La devise « " + code + " » a trois décimales et impose à Stripe un montant "
                    + "multiple de dix dans sa plus petite unité. Cette règle n'est pas "
                    + "implémentée : ajoutez-la, avec ses tests, avant d'encaisser dans cette "
                    + "devise.");
        }
        return montant
                .movePointRight(decimales(code))
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
    }
}

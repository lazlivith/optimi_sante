package com.optimisante.backend.domain.finance;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * La devise qu'un visiteur attend selon son pays.
 *
 * <p><b>Une suggestion, jamais une contrainte.</b> Un praticien congolais en déplacement à Paris
 * doit pouvoir payer en euros, et un client français qui expédie à Brazzaville doit pouvoir
 * annoncer un prix en francs CFA. Le pays décide de ce qui est proposé <i>en premier</i> ; le
 * choix explicite du visiteur l'emporte toujours.</p>
 *
 * <p>La table ne couvre que les zones monétaires, pas tous les pays : un pays absent retombe sur
 * l'euro, qui est la devise de référence de la plateforme.</p>
 */
public final class PaysDevise {

    private PaysDevise() {}

    /** Franc CFA d'Afrique centrale — zone CEMAC. */
    private static final String XAF = "XAF";
    /** Franc CFA d'Afrique de l'Ouest — zone UEMOA. */
    private static final String XOF = "XOF";

    private static final Map<String, String> PAR_PAYS = Map.ofEntries(
            // CEMAC
            Map.entry("CG", XAF), // Congo-Brazzaville
            Map.entry("CM", XAF), // Cameroun
            Map.entry("GA", XAF), // Gabon
            Map.entry("TD", XAF), // Tchad
            Map.entry("CF", XAF), // Centrafrique
            Map.entry("GQ", XAF), // Guinée équatoriale
            // UEMOA
            Map.entry("SN", XOF), // Sénégal
            Map.entry("CI", XOF), // Côte d'Ivoire
            Map.entry("BJ", XOF), // Bénin
            Map.entry("BF", XOF), // Burkina Faso
            Map.entry("ML", XOF), // Mali
            Map.entry("NE", XOF), // Niger
            Map.entry("TG", XOF), // Togo
            Map.entry("GW", XOF)  // Guinée-Bissau
    );

    /**
     * La devise suggérée pour ce pays, si la plateforme la sert.
     *
     * <p>Rend un résultat vide plutôt que l'euro par défaut : c'est à l'appelant de décider du
     * repli, et confondre « aucune suggestion » avec « l'euro » empêcherait de distinguer un
     * pays inconnu d'un pays de la zone euro.</p>
     */
    public static Optional<Devise> suggestion(String codePays) {
        if (codePays == null || codePays.isBlank()) {
            return Optional.empty();
        }
        String code = PAR_PAYS.get(codePays.trim().toUpperCase(Locale.ROOT));
        return Optional.ofNullable(code).map(Devise::de);
    }
}

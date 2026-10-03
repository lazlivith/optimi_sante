package com.optimisante.backend.domain.orders.shipping;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

/**
 * Les destinations desservies, regroupées par difficulté logistique.
 *
 * <p><b>Pourquoi un regroupement, et non un tarif par pays.</b> Quarante tarifs à tenir à jour
 * seraient quarante occasions de se tromper, pour une différence de coût qui, à l'intérieur
 * d'une même zone, tient surtout au transporteur et non au pays. Les zones suivent les
 * contraintes douanières réelles : le Maghreb, l'Afrique de l'Ouest et centrale — où passe
 * l'essentiel de l'activité — et le reste du continent.</p>
 *
 * <p><b>Un pays absent de cette liste n'est pas livré.</b> Il n'est pas facturé au tarif d'une
 * zone voisine : la commande est refusée avec une invitation à demander un devis de transport.
 * Facturer un forfait pour une destination qu'on ne sait pas servir reviendrait à encaisser
 * une promesse qu'on ne peut pas tenir.</p>
 *
 * <p>Les codes sont ceux de la norme ISO 3166-1 alpha-2, celle qu'emploient les transporteurs
 * et les déclarations douanières.</p>
 */
public enum ZoneLivraison {

    /** France métropolitaine. Les territoires d'outre-mer relèvent d'un régime distinct. */
    FRANCE("France métropolitaine", Set.of("FR")),

    AFRIQUE_NORD("Afrique du Nord",
            Set.of("MA", "DZ", "TN", "EG", "MR")),

    /** Le cœur de l'activité : les pays d'où viennent les praticiens accompagnés. */
    AFRIQUE_OUEST_CENTRE("Afrique de l'Ouest et centrale",
            Set.of("SN", "CI", "CM", "CG", "CD", "GA", "ML", "BF", "GN", "BJ", "TG", "NE",
                    "TD", "CF")),

    AFRIQUE_AUTRE("Afrique de l'Est et australe",
            Set.of("MG", "MU", "DJ", "RW", "BI", "KE", "ZA", "AO", "ET"));

    private final String libelle;
    private final Set<String> pays;

    ZoneLivraison(String libelle, Set<String> pays) {
        this.libelle = libelle;
        this.pays = pays;
    }

    public String libelle() {
        return libelle;
    }

    public Set<String> pays() {
        return pays;
    }

    /**
     * La zone d'un pays, ou vide si la destination n'est pas desservie.
     *
     * <p>Un {@link Optional} plutôt qu'une zone « reste du monde » : l'appelant est ainsi
     * obligé de traiter le cas, là où une valeur par défaut l'aurait laissé facturer
     * silencieusement une destination inconnue.</p>
     */
    public static Optional<ZoneLivraison> pour(String codePays) {
        if (codePays == null || codePays.isBlank()) {
            return Optional.empty();
        }
        String code = codePays.trim().toUpperCase();
        return Arrays.stream(values()).filter(z -> z.pays.contains(code)).findFirst();
    }
}

package com.optimisante.backend.infrastructure.legal;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Identité légale de l'entreprise, telle qu'elle s'imprime au pied de chaque document.
 *
 * <p>Ces valeurs étaient écrites en dur dans le gabarit du pied de page : un SIRET, une adresse
 * et un <b>IBAN inventés</b> s'imprimaient sur les devis, les reçus et les conventions envoyés
 * aux CHU. Un partenaire pouvait tenter un virement vers un compte qui n'existe pas.</p>
 *
 * <p><b>Le parti pris quand une valeur manque</b> : afficher « à compléter », jamais une valeur
 * plausible. Un document qui annonce « SIRET : à compléter » se corrige ; un document qui annonce
 * « SIRET : 123 456 789 00012 » se croit juste et circule. Voir {@link #ou(String)}.</p>
 *
 * @see CompanyIdentityGuard qui interdit le démarrage en production avec des valeurs manquantes
 */
@ConfigurationProperties(prefix = "app.legal")
public record CompanyIdentity(
        String denomination,
        String formeJuridique,
        String capitalSocial,
        String siret,
        String tvaIntracom,
        String rcs,
        String adresse,
        String iban,
        String bic,
        String email,
        String telephone,
        String numeroAgrement
) {

    /** Ce qui s'imprime à la place d'une valeur absente : visible, et impossible à confondre. */
    public static final String MANQUANT = "à compléter";

    /** Rend la valeur, ou la marque d'absence — jamais une chaîne vide qui passerait inaperçue. */
    private static String ou(String valeur) {
        return valeur == null || valeur.isBlank() ? MANQUANT : valeur.trim();
    }

    /**
     * Dénomination telle qu'elle doit s'imprimer dans le corps d'un document.
     *
     * <p>Passe par le repli, contrairement à l'accesseur brut du record : sans elle, une valeur
     * absente laissait « Entre , dont le siège est situé , » — une phrase amputée, qui se
     * remarque moins qu'une mention « à compléter » et se corrige donc plus tard.</p>
     */
    public String denominationAffichee() {
        return ou(denomination);
    }

    /** Adresse du siège, avec le même repli que ci-dessus. */
    public String adresseAffichee() {
        return ou(adresse);
    }

    /** Première ligne du pied de page : qui émet le document, et sous quelle immatriculation. */
    public String ligneIdentite() {
        return "%s %s%s — %s — SIRET : %s — TVA : %s".formatted(
                ou(denomination), ou(formeJuridique),
                capitalSocial == null || capitalSocial.isBlank() ? "" : " au capital de " + capitalSocial,
                ou(adresse), ou(siret), ou(tvaIntracom));
    }

    /** Deuxième ligne : les coordonnées bancaires, quand elles existent. */
    public String ligneBancaire() {
        return "IBAN : %s — BIC : %s".formatted(ou(iban), ou(bic));
    }

    /** Troisième ligne : comment joindre un humain. */
    public String ligneContact() {
        return "%s — Tél : %s".formatted(ou(email), ou(telephone));
    }

    /** Vrai dès qu'une mention obligatoire manque — ce que vérifie le garde au démarrage. */
    public boolean estIncomplete() {
        return ou(denomination).equals(MANQUANT)
                || ou(siret).equals(MANQUANT)
                || ou(adresse).equals(MANQUANT)
                || ou(tvaIntracom).equals(MANQUANT);
    }
}

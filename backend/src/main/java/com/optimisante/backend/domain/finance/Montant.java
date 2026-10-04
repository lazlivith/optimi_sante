package com.optimisante.backend.domain.finance;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

/**
 * Une somme d'argent : une valeur <b>et</b> sa devise, inséparables.
 *
 * <p><b>Ce que ce type empêche.</b> Un {@code BigDecimal} seul ne dit pas en quoi il est libellé.
 * Rien n'empêche alors d'additionner des euros et des francs CFA, ni d'envoyer à Stripe un
 * montant converti dans la mauvaise unité. Ici, additionner deux devises différentes ne
 * compile pas mieux qu'il ne s'exécute : c'est une erreur, et elle est dite.</p>
 *
 * <p><b>Les arrondis sont explicites.</b> Toute opération qui peut créer des décimales de trop
 * — une conversion, une remise — rend un montant déjà ramené à la précision de sa devise. Un
 * montant qui circule est donc toujours représentable : il n'existe pas de 327 978,5 XAF.</p>
 */
public record Montant(BigDecimal valeur, Devise devise) {

    public Montant {
        if (valeur == null) {
            throw new IllegalArgumentException("Montant sans valeur.");
        }
        if (devise == null) {
            throw new IllegalArgumentException("Montant sans devise : " + valeur);
        }
        // Normalisation a la construction : deux montants egaux doivent l'etre aussi au sens
        // d'equals, ce que des echelles differentes empecheraient (10.0 et 10.00).
        valeur = valeur.setScale(devise.decimales(), RoundingMode.HALF_UP);
    }

    public static Montant de(BigDecimal valeur, Devise devise) {
        return new Montant(valeur, devise);
    }

    public static Montant de(BigDecimal valeur, String codeDevise) {
        return new Montant(valeur, Devise.de(codeDevise));
    }

    /** Un montant en euros, la devise dans laquelle la plateforme stocke ses prix. */
    public static Montant euros(BigDecimal valeur) {
        return new Montant(valeur, Devise.REFERENCE);
    }

    public static Montant zero(Devise devise) {
        return new Montant(BigDecimal.ZERO, devise);
    }

    public Montant plus(Montant autre) {
        exigerMemeDevise(autre, "additionner");
        return new Montant(valeur.add(autre.valeur), devise);
    }

    public Montant moins(Montant autre) {
        exigerMemeDevise(autre, "soustraire");
        return new Montant(valeur.subtract(autre.valeur), devise);
    }

    public Montant fois(int quantite) {
        return new Montant(valeur.multiply(BigDecimal.valueOf(quantite)), devise);
    }

    public boolean estNul() {
        return valeur.signum() == 0;
    }

    public boolean estPositif() {
        return valeur.signum() > 0;
    }

    public boolean plusGrandQue(Montant autre) {
        exigerMemeDevise(autre, "comparer");
        return valeur.compareTo(autre.valeur) > 0;
    }

    /**
     * Le montant dans la plus petite unité de sa devise, tel que Stripe l'attend.
     *
     * <p>Centimes pour l'euro, unités pour le franc CFA. C'est la seule frontière où un montant
     * cesse d'être une valeur décimale pour devenir un entier.</p>
     *
     * @throws UnsupportedOperationException si la devise a trois décimales, cas que la
     *                                       plateforme refuse plutôt que d'approximer
     */
    public long versPlusPetiteUnite() {
        if (!devise.gereeParStripe()) {
            throw new UnsupportedOperationException(
                    "La devise " + devise + " a trois décimales et impose à Stripe un montant "
                    + "multiple de dix dans sa plus petite unité. Cette règle n'est pas "
                    + "implémentée : ajoutez-la, avec ses tests, avant d'encaisser dans cette "
                    + "devise.");
        }
        if (valeur.signum() < 0) {
            throw new IllegalArgumentException("Montant négatif envoyé à Stripe : " + this);
        }
        return valeur.movePointRight(devise.decimales())
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
    }

    /** L'opération inverse : un montant reçu de Stripe, exprimé en sa plus petite unité. */
    public static Montant depuisPlusPetiteUnite(long valeur, Devise devise) {
        return new Montant(BigDecimal.valueOf(valeur).movePointLeft(devise.decimales()), devise);
    }

    private void exigerMemeDevise(Montant autre, String operation) {
        if (autre == null) {
            throw new IllegalArgumentException("Montant absent pour " + operation + ".");
        }
        if (!devise.equals(autre.devise)) {
            throw new IllegalArgumentException(
                    "Impossible de " + operation + " des montants de devises différentes : "
                    + this + " et " + autre + ". Convertissez d'abord vers une devise commune.");
        }
    }

    @Override
    public String toString() {
        return String.format(Locale.FRENCH, "%,." + devise.decimales() + "f %s", valeur, devise);
    }
}

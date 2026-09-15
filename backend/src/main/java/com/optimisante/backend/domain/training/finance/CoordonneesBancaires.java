package com.optimisante.backend.domain.training.finance;

import java.math.BigInteger;
import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Contrôle et mise en forme des coordonnées bancaires et des textes d'un virement SEPA.
 *
 * <p><b>Pourquoi vérifier la clé de l'IBAN.</b> Un chiffre inversé en recopiant un RIB produit un
 * IBAN au bon format mais faux. Le virement part, la banque le rejette plusieurs jours plus tard,
 * et le CHU attend un argent qui revient. La clé ISO 13616 (reste 1 modulo 97) détecte toute
 * erreur d'un seul caractère et toute inversion de deux caractères voisins : on refuse la saisie
 * au lieu de découvrir l'erreur au retour du virement.</p>
 */
public final class CoordonneesBancaires {

    private static final Pattern FORME_IBAN = Pattern.compile("^[A-Z]{2}[0-9]{2}[A-Z0-9]{11,30}$");
    private static final Pattern FORME_BIC = Pattern.compile("^[A-Z]{6}[A-Z0-9]{2}([A-Z0-9]{3})?$");
    private static final BigInteger QUATRE_VINGT_DIX_SEPT = BigInteger.valueOf(97);

    private CoordonneesBancaires() {
    }

    /** Retire espaces et tirets, passe en majuscules : la forme saisie importe peu. */
    public static String normaliser(String valeur) {
        return valeur == null ? null : valeur.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
    }

    /**
     * @return l'IBAN normalisé
     * @throws IllegalArgumentException si le format ou la clé de contrôle est faux
     */
    public static String validerIban(String saisie) {
        String iban = normaliser(saisie);
        if (iban == null || !FORME_IBAN.matcher(iban).matches()) {
            throw new IllegalArgumentException("IBAN mal formé : 2 lettres de pays, 2 chiffres de clé, "
                    + "puis le numéro de compte.");
        }
        // Les quatre premiers caractères passent à la fin, chaque lettre devient deux chiffres
        // (A = 10 … Z = 35) : le nombre obtenu doit laisser un reste de 1 dans la division par 97.
        String rearrange = iban.substring(4) + iban.substring(0, 4);
        StringBuilder chiffres = new StringBuilder();
        for (char c : rearrange.toCharArray()) {
            chiffres.append(Character.isLetter(c) ? String.valueOf(c - 'A' + 10) : String.valueOf(c));
        }
        if (!new BigInteger(chiffres.toString()).mod(QUATRE_VINGT_DIX_SEPT).equals(BigInteger.ONE)) {
            throw new IllegalArgumentException("IBAN invalide : la clé de contrôle ne correspond pas. "
                    + "Vérifiez qu'aucun chiffre n'a été inversé en le recopiant.");
        }
        return iban;
    }

    /** @return le BIC normalisé, ou {@code null} s'il n'est pas fourni */
    public static String validerBic(String saisie) {
        String bic = normaliser(saisie);
        if (bic == null || bic.isEmpty()) {
            return null;
        }
        if (!FORME_BIC.matcher(bic).matches()) {
            throw new IllegalArgumentException("BIC mal formé : 8 ou 11 caractères (ex. BNPAFRPP).");
        }
        return bic;
    }

    /**
     * « FR14 **** **** 2606 » : le pays et les quatre derniers chiffres, assez pour reconnaître le
     * compte, pas pour le recopier. Les quatre derniers restent groupés — regroupés par quatre
     * depuis le début, un IBAN de 27 caractères les coupait en « ***2 606 ».
     */
    public static String masquer(String iban) {
        if (iban == null || iban.length() < 8) {
            return iban;
        }
        return iban.substring(0, 4) + " **** **** " + iban.substring(iban.length() - 4);
    }

    public static String groupesDeQuatre(String valeur) {
        return valeur == null ? null : valeur.replaceAll("(.{4})(?!$)", "$1 ");
    }

    /**
     * Ramène un texte au jeu de caractères SEPA : lettres latines sans accent, chiffres et
     * {@code / - ? : ( ) . , ' +} et l'espace. Une banque qui reçoit « Hôpital » peut rejeter le
     * fichier entier, ou remplacer le caractère par un point d'interrogation dans le libellé que
     * lira le comptable du CHU.
     */
    public static String texteSepa(String texte, int longueurMax) {
        if (texte == null) {
            return "";
        }
        String sansAccents = Normalizer.normalize(texte, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace('’', '\'');
        String propre = sansAccents.replaceAll("[^A-Za-z0-9/\\-?:().,'+ ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return propre.length() > longueurMax ? propre.substring(0, longueurMax) : propre;
    }
}

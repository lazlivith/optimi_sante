package com.optimisante.backend.common.storage;

import java.util.Locale;

/**
 * L'extension à faire figurer dans l'adresse d'une image servie.
 *
 * <p><b>Pourquoi l'extension ne peut pas être omise.</b> Notre compte Cloudinary ne sert une
 * adresse sans extension que pour le WebP ; pour tout le reste, il répond 401. Une couverture
 * de blog en JPEG était donc déposée, facturée, et s'affichait cassée — sans rien dans les
 * journaux pour le dire, puisque le dépôt, lui, avait réussi.</p>
 *
 * <p><b>Et pourquoi il faut le VRAI format, pas un format commode.</b> Demander {@code .webp}
 * ou {@code .png} pour une image stockée en JPEG revient à demander une conversion, c'est-à-dire
 * une transformation — que ce compte refuse également, avec le même 401. Mesuré : seul le format
 * réel passe. La transformation {@code f_auto}, que Cloudinary recommande pourtant pour ce cas,
 * échoue pour la même raison.</p>
 */
public final class FormatImage {

    private FormatImage() {
    }

    /**
     * @param typeMime celui qu'annonce le navigateur, déjà validé par l'appelant
     * @return l'extension sans point ; {@code jpg} par défaut, qui est ce que Cloudinary
     *         retient pour {@code image/jpeg} comme pour {@code image/jpg}
     */
    public static String depuisTypeMime(String typeMime) {
        if (typeMime == null) {
            return "jpg";
        }
        return switch (typeMime.trim().toLowerCase(Locale.ROOT)) {
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            case "image/avif" -> "avif";
            case "image/gif" -> "gif";
            default -> "jpg";
        };
    }

    /** L'extension d'un nom de fichier, sans point, ou {@code jpg} s'il n'en porte pas. */
    public static String depuisNomFichier(String nomFichier) {
        int point = nomFichier == null ? -1 : nomFichier.lastIndexOf('.');
        if (point < 0 || point == nomFichier.length() - 1) {
            return "jpg";
        }
        String brute = nomFichier.substring(point + 1).replaceAll("[^A-Za-z0-9]", "");
        return brute.isEmpty() || brute.length() > 5 ? "jpg" : brute.toLowerCase(Locale.ROOT);
    }
}

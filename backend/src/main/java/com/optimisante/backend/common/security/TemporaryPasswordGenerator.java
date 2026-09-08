package com.optimisante.backend.common.security;

import java.security.SecureRandom;

/**
 * Génère les mots de passe provisoires des comptes provisionnés par l'administration
 * (partenaire CHU validé, médecin dont la candidature est payée) ainsi que lors d'un
 * renvoi d'identifiants.
 *
 * Extrait de PartnershipService et DoctorApplicationService, qui en portaient chacun
 * une copie strictement identique.
 *
 * L'alphabet exclut volontairement les caractères ambigus (O/0, I/l/1) : ces mots de
 * passe sont lus dans un email puis retapés à la main.
 */
public final class TemporaryPasswordGenerator {

    private static final String PASSWORD_CHARS =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int LENGTH = 12;

    private TemporaryPasswordGenerator() {
    }

    public static String generate() {
        StringBuilder sb = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            sb.append(PASSWORD_CHARS.charAt(RANDOM.nextInt(PASSWORD_CHARS.length())));
        }
        return sb.toString();
    }
}

package com.optimisante.backend.config.exception;

/**
 * Échec d'authentification métier (identifiants invalides, compte désactivé) — distinct des
 * autres RuntimeException "métier" du projet (mappées en 400 par GlobalExceptionHandler) car
 * le frontend (LoginPage.tsx) distingue déjà explicitement le cas "mauvais identifiants" via le
 * code HTTP 401 pour afficher un message dédié ("Identifiants incorrects."). Doit rester 401.
 */
public class AuthenticationFailedException extends RuntimeException {
    public AuthenticationFailedException(String message) {
        super(message);
    }
}

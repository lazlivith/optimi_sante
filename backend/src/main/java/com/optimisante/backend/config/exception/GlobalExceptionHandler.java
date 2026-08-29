package com.optimisante.backend.config.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * Gestionnaire d'exceptions global — absent depuis le début du projet, ce qui faisait qu'une
 * exception métier non catchée (email déjà utilisé, dossier déjà traité, code promo invalide,
 * ressource introuvable...) traversait le dispatch d'erreur Spring Security et ressortait en
 * 401 avec un corps vide, au lieu du code HTTP approprié avec un vrai message. Conséquence
 * concrète et confusante côté frontend : l'intercepteur Axios déconnecte automatiquement
 * l'utilisateur sur tout 401/403 (comportement voulu pour une vraie expiration de session),
 * donc une simple erreur métier ("email déjà utilisé", "dossier déjà validé"...) provoquait une
 * déconnexion brutale et incompréhensible, comme si la session avait expiré.
 * <p>
 * Important : ce gestionnaire n'intercepte que les exceptions remontant depuis les méthodes de
 * contrôleur. Les échecs d'authentification réels (token absent/invalide) restent gérés en amont
 * par les filtres Spring Security et produisent un vrai 401. Les refus d'autorisation
 * (@PreAuthorize) lèvent en revanche une AccessDeniedException qui remonte À TRAVERS ce
 * ControllerAdvice (elle est levée par le proxy AOP pendant l'exécution du contrôleur, donc
 * avant de pouvoir atteindre le filtre de sécurité) — d'où le handler dédié ci-dessous qui la
 * remappe explicitement en 403 au lieu de tomber dans le filet générique à 400.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(e -> e.getField() + " : " + e.getDefaultMessage())
                .orElse("Requête invalide.");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", message));
    }

    /**
     * Doit rester 401 (contrairement au reste des RuntimeException métier, mappées en 400) :
     * LoginPage.tsx distingue explicitement ce code pour afficher "Identifiants incorrects.".
     */
    @ExceptionHandler(AuthenticationFailedException.class)
    public ResponseEntity<Map<String, String>> handleAuthenticationFailed(AuthenticationFailedException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("message", ex.getMessage() != null ? ex.getMessage() : "Authentification échouée."));
    }

    /**
     * @PreAuthorize refusé lève une AccessDeniedException (sous-classe de RuntimeException) qui,
     * sans ce handler dédié, tombait dans le filet générique ci-dessous et ressortait en 400 au
     * lieu de 403 — cassant la sémantique HTTP ET le comportement de déconnexion automatique du
     * frontend (qui ne réagit qu'à 401/403). Doit être déclaré explicitement : Spring choisit
     * toujours le handler le plus spécifique, donc celui-ci prime sur handleRuntime ci-dessous
     * indépendamment de l'ordre de déclaration dans la classe.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("message", "Vous n'avez pas les droits nécessaires pour effectuer cette action."));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", ex.getMessage() != null ? ex.getMessage() : "Requête invalide."));
    }

    /**
     * La grande majorité des règles métier du projet (dossier déjà traité, code déjà utilisé,
     * places épuisées, code promo expiré...) sont exprimées via IllegalStateException — mappé
     * en 409 Conflict, sémantiquement le plus proche de "état actuel incompatible avec l'action".
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleIllegalState(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("message", ex.getMessage() != null ? ex.getMessage() : "Action impossible dans l'état actuel."));
    }

    /**
     * Filet de sécurité pour les nombreux endroits du projet qui lèvent un RuntimeException("...")
     * générique en guise d'exception métier ad hoc (ex. "Product not found", "Email already
     * exists") — 400 plutôt que 500, ces cas relevant d'une requête client incorrecte, pas d'un
     * bug serveur. Le vrai message métier est transmis au frontend au lieu d'un corps vide.
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handleRuntime(RuntimeException ex) {
        log.warn("Business exception: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", ex.getMessage() != null ? ex.getMessage() : "Une erreur est survenue."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", "Une erreur interne est survenue. Réessayez plus tard."));
    }
}

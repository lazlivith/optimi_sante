package com.optimisante.backend.config.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.Arrays;
import org.springframework.http.converter.HttpMessageNotReadableException;
import tools.jackson.databind.exc.InvalidFormatException;

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

    /**
     * Corps de requête illisible ou valeur d'enum inconnue (ex. facilityType invalide).
     * Sans ce handler, Jackson renvoyait son message brut, exposant le nom de classe Java
     * interne (`com.optimisante.backend.domain.identity.entity.FacilityType`) au client :
     * message illisible pour l'utilisateur et divulgation inutile de la structure du code.
     */
    /**
     * Paramètre de requête absent ou de type incompatible.
     *
     * <p>Sans ce traitement, ces deux cas tombaient dans le gestionnaire générique et
     * répondaient <b>500 « Une erreur interne est survenue »</b>. Or rien n'est en panne :
     * l'appelant a simplement omis un paramètre. Le message envoyait chercher un défaut de
     * serveur inexistant, alors que la correction est du côté de l'appel — et il nommait
     * d'autant moins la cause que le paramètre manquant, lui, était connu.</p>
     */
    /**
     * Fichier plus lourd que ce que le servlet accepte.
     *
     * <p>Sans ce traitement, un televersement trop volumineux remontait en <b>500</b> : une
     * panne serveur, aux yeux de l'administrateur, alors qu'il suffisait de compresser la
     * video. La limite metier de ProductMediaService reste la premiere a repondre — elle nomme
     * la taille du fichier recu — mais elle ne peut rien dire si le servlet coupe avant elle.
     * Ce filet ne se declenche donc que si les deux limites ont ete mal alignees, ou sur un
     * envoi multiple dont la somme depasse la requete.</p>
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, String>> handleUploadTooLarge(MaxUploadSizeExceededException ex) {
        log.warn("Téléversement refusé, taille dépassée : {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(Map.of("message",
                "Le fichier envoyé est trop volumineux. La limite est de 50 Mo pour une vidéo "
                + "et de 10 Mo pour une image."));
    }

    @ExceptionHandler({MissingServletRequestParameterException.class,
                       MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Map<String, String>> handleBadParameter(Exception ex) {
        String message = ex instanceof MissingServletRequestParameterException manquant
                ? "Paramètre obligatoire absent : « " + manquant.getParameterName() + " »."
                : "Paramètre invalide : « "
                    + ((MethodArgumentTypeMismatchException) ex).getName() + " ».";
        log.warn("Paramètre de requête refusé : {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleUnreadableBody(HttpMessageNotReadableException ex) {
        String message = "Requête invalide : une valeur envoyée n'est pas reconnue.";
        Throwable cause = ex.getCause();
        // NB : Spring Boot 4 embarque Jackson 3 (`tools.jackson`). Jackson 2
        // (`com.fasterxml`) reste présent en transitif via JJWT : importer la mauvaise
        // classe compile sans erreur mais ne matche jamais à l'exécution.
        if (cause instanceof InvalidFormatException ife && ife.getTargetType() != null
                && ife.getTargetType().isEnum()) {
            String accepted = Arrays.stream(ife.getTargetType().getEnumConstants())
                    .map(Object::toString)
                    .collect(Collectors.joining(", "));
            message = "Valeur non reconnue : \"" + ife.getValue() + "\". Valeurs acceptées : " + accepted + ".";
        }
        log.warn("Corps de requête illisible : {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", message));
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

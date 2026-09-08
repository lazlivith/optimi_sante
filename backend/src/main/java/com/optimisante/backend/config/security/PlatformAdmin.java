package com.optimisante.backend.config.security;

import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.*;

/**
 * Réservé à la gouvernance de la plateforme : gestion des comptes utilisateurs.
 *
 * <p>Créer ou désactiver un administrateur n'est pas une tâche métier : c'est ce qui décide
 * qui voit quoi. À terme, ce périmètre appartient au seul {@code SUPER_ADMIN}, afin qu'un
 * administrateur métier ne puisse pas s'attribuer — ni attribuer à un tiers — un périmètre
 * qu'il n'a pas.</p>
 *
 * <p><b>Pourquoi {@code ADMIN} y figure encore.</b> La plateforme ne compte aujourd'hui
 * aucun compte {@code SUPER_ADMIN} : restreindre immédiatement cette annotation au seul
 * super administrateur rendrait la gestion des comptes inaccessible à tout le monde, y
 * compris pour créer le premier super administrateur. Le rôle hérité est donc conservé ici
 * comme dans les deux annotations métier, et disparaîtra avec lui.</p>
 *
 * <p><b>Étape de sortie</b> : promouvoir un compte en {@code SUPER_ADMIN}, réaffecter les
 * autres administrateurs à leur métier, puis retirer {@code ADMIN} des trois annotations et
 * de la contrainte SQL dans une migration dédiée.</p>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
public @interface PlatformAdmin {
}

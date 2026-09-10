package com.optimisante.backend.config.security;

import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.*;

/**
 * Réservé à l'administration de la mobilité : dossiers de candidature, validation des
 * formations, demandes de partenariat, reversements aux CHU.
 *
 * <p>C'est le périmètre le plus sensible de la plateforme : il donne accès aux pièces
 * médicales et consulaires des candidats. Un administrateur du négoce n'a rien à y faire,
 * et la restriction doit valoir pour l'API autant que pour l'interface.</p>
 *
 * <p>{@code ADMIN} y figure encore : ce rôle hérité reste accepté par les deux univers le
 * temps de la bascule des comptes (V30).</p>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@PreAuthorize("hasAnyRole('ADMIN_MOBILITE', 'ADMIN', 'SUPER_ADMIN')")
public @interface MobilityAdmin {
}

package com.optimisante.backend.config.security;

import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.*;

/**
 * Infrastructure partagée par les deux univers d'administration — typiquement le
 * téléversement générique de fichiers, utilisé aussi bien pour un visuel de produit que
 * pour un média de formation.
 *
 * <p>À n'employer que lorsqu'un endpoint sert réellement les deux métiers. Dans le doute,
 * préférer {@link EcommerceAdmin} ou {@link MobilityAdmin} : ouvrir large est facile,
 * refermer ensuite l'est beaucoup moins.</p>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@PreAuthorize("hasAnyRole('ADMIN_ECOMMERCE', 'ADMIN_MOBILITE', 'ADMIN', 'SUPER_ADMIN')")
public @interface AnyAdmin {
}

package com.optimisante.backend.config.security;

import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.*;

/**
 * Réservé à l'administration du négoce : catalogue, commandes, devis B2B, codes promo,
 * finance des ventes.
 *
 * <p>Pourquoi une annotation plutôt qu'un {@code @PreAuthorize} écrit sur place : la règle
 * énumère trois rôles et s'applique à une trentaine d'endpoints. Répétée à la main, elle
 * finit par diverger — un oubli passe inaperçu jusqu'à ce qu'un administrateur accède à ce
 * qu'il ne devrait pas voir. Ici, la règle vit à un seul endroit et l'intention est lisible
 * sur chaque contrôleur.</p>
 *
 * <p>{@code ADMIN} y figure encore : ce rôle hérité reste accepté par les deux univers le
 * temps de la bascule des comptes (V30).</p>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@PreAuthorize("hasAnyRole('ADMIN_ECOMMERCE', 'ADMIN', 'SUPER_ADMIN')")
public @interface EcommerceAdmin {
}

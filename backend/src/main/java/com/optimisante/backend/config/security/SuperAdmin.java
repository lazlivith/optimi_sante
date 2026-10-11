package com.optimisante.backend.config.security;

import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.*;

/**
 * Réservé au seul {@code SUPER_ADMIN}.
 *
 * <p><b>Pourquoi une quatrième annotation.</b> {@link PlatformAdmin} laisse encore passer le
 * rôle hérité {@code ADMIN}, pour la raison décrite dans sa javadoc : la plateforme n'avait
 * aucun super administrateur, et le restreindre aurait rendu la gestion des comptes
 * inaccessible à tous. Ce n'est plus le cas, mais retirer {@code ADMIN} des trois annotations
 * existantes touche l'ensemble de l'administration — c'est un nettoyage à mener pour
 * lui-même, pas en marge d'une fonctionnalité.</p>
 *
 * <p>Cette annotation permet donc de poser dès maintenant la restriction stricte là où elle
 * est voulue : les <b>prix</b>, qui engagent le chiffre d'affaires, et la <b>restauration
 * d'une version publiée</b>, qui réécrit ce que le public lit.</p>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@PreAuthorize("hasRole('SUPER_ADMIN')")
public @interface SuperAdmin {
}

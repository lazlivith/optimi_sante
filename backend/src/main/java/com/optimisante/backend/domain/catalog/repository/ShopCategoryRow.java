package com.optimisante.backend.domain.catalog.repository;

import java.util.UUID;

/**
 * Projection native : ce qu'il faut pour présenter une catégorie en boutique.
 *
 * <p><b>Pourquoi une image.</b> La table {@code categories} n'en porte pas. La page d'accueil
 * en affichait donc de fausses : huit photos Unsplash choisies par mot-clé dans le nom de la
 * catégorie, avec un tableau de repli quand aucun mot ne tombait. Un visiteur voyait un
 * stéthoscope sur une catégorie de mobilier. L'image remontée ici est celle d'un vrai produit
 * du rayon — il n'y a rien à inventer, le catalogue contient 1 486 photos.</p>
 *
 * <p><b>Pourquoi la plus ancienne.</b> Le choix doit être <i>stable</i> : une vignette qui
 * change de photo à chaque rechargement passe pour un défaut d'affichage. {@code created_at}
 * puis {@code id} départagent sans ambiguïté, y compris entre deux produits importés dans la
 * même seconde.</p>
 *
 * <p><b>Pourquoi ce comptage diffère de celui de l'administration.</b> {@link CategoryUsageRow}
 * compte les produits désactivés, pour que l'administrateur ne croie pas vide une catégorie
 * qu'il a simplement mise en sommeil. Ici, c'est l'inverse : une catégorie dont aucun produit
 * n'est achetable <i>est</i> vide pour le visiteur, et l'y envoyer le mène à une page sans
 * rien. Requête native pour la même raison que l'autre — le {@code @SQLRestriction} porté par
 * {@code Product} s'appliquerait sinon en silence, ici comme une coïncidence et non comme une
 * décision.</p>
 */
public interface ShopCategoryRow {
    UUID getId();
    long getProductCount();
    String getImageUrl();
}

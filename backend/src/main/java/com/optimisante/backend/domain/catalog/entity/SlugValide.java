package com.optimisante.backend.domain.catalog.entity;

import java.util.regex.Pattern;

/**
 * Ce qu'un slug a le droit d'être, et le seul endroit qui le dit.
 *
 * <p><b>L'incident du 23 octobre.</b> 148 produits et une catégorie portaient un slug
 * contenant un {@code %} — un {@code ®} encodé en texte par un import ancien. Correctement
 * encodé dans l'adresse, ce {@code %} devient {@code %25}, que le pare-feu de Spring Security
 * refuse avant même le contrôleur : 401, corps vide, sur une route publique. Dix pour cent du
 * catalogue était injoignable, et l'écran renvoyait vers la page de connexion.</p>
 *
 * <p><b>Pourquoi la vérification vit ici et pas dans un service.</b> Deux routes écrivent des
 * produits — l'administration et l'import fournisseur — et une troisième arrivera avec La
 * Centrale Médicale. Une règle posée dans l'une ne protège pas les autres, et c'est
 * exactement ainsi que ces 148 slugs sont entrés : le générateur de l'administration était
 * déjà correct, l'import ne passait pas par lui. Un rappel de cycle de vie JPA, lui, se
 * déclenche sur <b>tout</b> enregistrement, quelle que soit la route qui l'a demandé.</p>
 */
public final class SlugValide {

    /** Minuscules, chiffres, tirets. Rien d'autre, et jamais vide. */
    public static final Pattern MOTIF = Pattern.compile("^[a-z0-9-]+$");

    private SlugValide() {
    }

    /**
     * Refuse l'enregistrement si le slug sort du motif.
     *
     * <p>Lever plutôt que corriger en silence : un slug hors motif vient d'un défaut en amont
     * — un générateur oublié, une saisie collée — et le corriger ici masquerait ce défaut
     * tout en changeant l'adresse d'une fiche sans que personne ne l'ait demandé.</p>
     *
     * @param quoi ce qu'on enregistre, pour que le message dise où regarder
     */
    public static void exiger(String quoi, String slug) {
        if (slug == null || !MOTIF.matcher(slug).matches()) {
            throw new IllegalArgumentException(
                    "Slug invalide pour " + quoi + " : « " + slug + " ». "
                    + "Seuls les minuscules, les chiffres et les tirets sont acceptés.");
        }
    }
}

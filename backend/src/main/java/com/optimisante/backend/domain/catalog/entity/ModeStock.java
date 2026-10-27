package com.optimisante.backend.domain.catalog.entity;

/**
 * Ce qu'on sait du stock d'un produit — et donc ce qu'on s'autorise à en faire.
 *
 * <p><b>Le mode ne dit pas « ce produit est en stock ».</b> Il dit « ce chiffre est-il tenu à
 * jour, et par qui ». C'est la distinction qui manquait : l'absence d'inventaire et une
 * rupture réelle s'écrivaient de la même façon, et le serveur refusait des commandes sur la
 * foi d'un nombre que personne n'avait jamais compté.</p>
 *
 * <p>Mesure du 27 octobre 2026 : <b>1 464 produits sur 1 497</b> portaient le 50 posé par
 * défaut à l'import. Au cinquantième exemplaire vendu de l'un d'eux, il serait devenu
 * invendable sans que rien ne manque en entrepôt.</p>
 */
public enum ModeStock {

    /**
     * Personne ne compte. Le chiffre est indicatif et ne bloque rien.
     *
     * <p>L'état de départ de tout le catalogue, et le défaut en base : c'est un constat, pas
     * une précaution.</p>
     */
    NON_SUIVI,

    /**
     * Nous comptons, dans notre entrepôt : vérifié, réservé, décrémenté.
     *
     * <p>Un produit n'y entre que par un inventaire — jamais par un import, qui décrit
     * l'entrepôt de quelqu'un d'autre.</p>
     */
    PROPRE,

    /**
     * Le fournisseur stocke et expédie pour notre compte.
     *
     * <p>Sa quantité est une photographie de <b>son</b> entrepôt, datée du dernier import.
     * Elle s'affiche et se journalise, mais ne refuse aucune commande : c'est lui qui sait
     * ce qu'il peut expédier, pas nous.</p>
     */
    FOURNISSEUR;

    /**
     * Ce stock est-il tenu par nous, et donc opposable à un client ?
     *
     * <p>Le seul test que le reste du code doit faire. L'écrire en clair ailleurs
     * (« {@code != NON_SUIVI} », « {@code == PROPRE} ») disperserait la règle, et c'est très
     * exactement ainsi que la règle des visuels factices avait fini en quatre exemplaires
     * divergents.</p>
     */
    public boolean estOpposable() {
        return this == PROPRE;
    }
}

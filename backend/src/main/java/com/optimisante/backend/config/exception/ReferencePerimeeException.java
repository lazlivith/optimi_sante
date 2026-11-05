package com.optimisante.backend.config.exception;

/**
 * La référence d'une pièce déposée n'est pas une référence que ce serveur a délivrée.
 *
 * <p><b>Pourquoi une exception à part, et pas un {@code IllegalArgumentException}.</b> Ce cas a
 * une cause précise et une seule : le navigateur tient encore la version de la page d'<b>avant
 * DP-4</b>, dans laquelle le dépôt rendait une clé de stockage. Il renvoie donc une clé là où le
 * serveur attend une référence signée. Ce n'est ni une requête malformée ni une faute de
 * l'utilisateur : c'est un décalage de version, et il se répare en rechargeant la page.</p>
 *
 * <p><b>Ce qu'on ne fait pas.</b> Accepter la clé « puisqu'elle arrive au mauvais moment »
 * rouvrirait exactement la porte que DP-4 ferme : une clé de stockage fournie par un client ne
 * sera jamais réacceptée, quelle que soit la raison de son arrivée.</p>
 *
 * <p>Porte un {@link #CODE} parce que l'écran doit pouvoir distinguer ce cas <b>sans comparer un
 * texte</b> : un message se reformule, un code non.</p>
 */
public class ReferencePerimeeException extends RuntimeException {

    /** Code stable, lu par l'écran d'inscription. Ne pas renommer sans corriger le frontend. */
    public static final String CODE = "REFERENCE_PERIMEE";

    /** Rédigé pour le candidat, et montré tel quel. */
    public static final String MESSAGE =
            "La page a été mise à jour, merci de la recharger avant de déposer vos pièces.";

    public ReferencePerimeeException() {
        super(MESSAGE);
    }
}

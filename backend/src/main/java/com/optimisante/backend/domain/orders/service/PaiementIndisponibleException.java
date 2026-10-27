package com.optimisante.backend.domain.orders.service;

/**
 * Le prestataire de paiement n'a pas pu être employé — la requête du client, elle, était juste.
 *
 * <p><b>Pourquoi une exception à elle seule.</b> Ce chemin levait un {@code RuntimeException}
 * nu, que le filet générique de {@code GlobalExceptionHandler} rend en <b>400</b>. Or 400
 * annonce « ta requête est fausse » : l'écran ne pouvait pas distinguer une panne de Stripe
 * d'une commande mal formée, et les journaux non plus. Un 502 dit ce qui s'est réellement
 * passé — un intermédiaire a échoué — et c'est la seule information qui permette de décider
 * s'il faut réessayer.</p>
 */
public class PaiementIndisponibleException extends RuntimeException {

    /** Le seul texte montré au visiteur, quelle que soit la panne en dessous. */
    public static final String MESSAGE =
            "Le paiement par carte est momentanément indisponible, réessayez plus tard ou contactez-nous.";

    public PaiementIndisponibleException(Throwable cause) {
        super(MESSAGE, cause);
    }
}

/**
 * La clé publique Stripe, et ce qu'on fait quand elle manque.
 *
 * <p>Vite inscrit la valeur de `VITE_STRIPE_PUBLISHABLE_KEY` **dans le code compilé** : elle
 * est figée à la construction, pas lue au démarrage. Une variable absente ne produit donc
 * aucune erreur de serveur — elle produit une chaîne vide dans le paquet servi, puis, au
 * moment de payer, un `IntegrationError` dans la console du visiteur et un cadre de paiement
 * vide à l'écran.</p>
 *
 * <p>C'est ce qui est arrivé le 27 octobre : la clé secrète avait bien été posée côté serveur,
 * mais la clé publique est lue par le **frontend**, donc par l'hébergeur du frontend, et une
 * reconstruction est nécessaire après l'avoir posée. Rien dans l'application ne le disait.</p>
 */

/** La clé telle qu'elle sera servie au navigateur, déjà débarrassée des espaces. */
export const CLE_PUBLIQUE_STRIPE: string =
  (import.meta.env.VITE_STRIPE_PUBLISHABLE_KEY ?? '').trim();

/**
 * Le texte montré au visiteur quand le paiement par carte ne peut pas s'afficher.
 *
 * <p>Identique au message que le serveur renvoie avec un 502 : quelle que soit la panne, le
 * visiteur lit une seule et même phrase.</p>
 */
export const MESSAGE_PAIEMENT_INDISPONIBLE =
  'Le paiement par carte est momentanément indisponible, réessayez plus tard ou contactez-nous.';

/**
 * Cette clé peut-elle servir ?
 *
 * <p>On vérifie le préfixe et pas seulement la présence : une variable mal recopiée — la clé
 * secrète à la place de la publique, une valeur d'exemple — échouerait de la même façon, en
 * silence, et mérite le même traitement. `pk_test_` et `pk_live_` sont les deux seules formes
 * qu'accepte `loadStripe`.</p>
 */
export function clePubliqueUtilisable(cle: string = CLE_PUBLIQUE_STRIPE): boolean {
  return cle.startsWith('pk_test_') || cle.startsWith('pk_live_');
}

/**
 * Combien de fois réessayer un appel qui a échoué.
 *
 * <p><b>Mesuré le 23 octobre.</b> Une fiche produit dont l'adresse ne désigne rien mettait
 * <b>9,7 secondes</b> à afficher sa page « ce produit n'existe pas » : react-query réessayait
 * trois fois un appel voué à échouer, et le visiteur lisait « Chargement… » pendant presque
 * dix secondes avant d'apprendre qu'il n'y avait rien à charger. La même page sans appel
 * réseau répondait en 2,4 s.</p>
 *
 * <p>Un 404 est une réponse, pas une panne : le serveur a compris la question et dit que la
 * chose n'existe pas. La reposer ne la fera pas exister. Les autres erreurs — un réseau
 * coupé, un serveur qui redémarre, une coupure de quelques secondes — valent un second
 * essai, et un seul : <b>l'appel est tenté deux fois au plus</b>.</p>
 */
export function reessayer(nombreEchecs: number, erreur: unknown): boolean {
  const statut = (erreur as { response?: { status?: number } })?.response?.status;
  if (statut === 404) return false;
  // `nombreEchecs` vaut 1 après le premier échec : un seul nouvel essai, donc deux appels.
  return nombreEchecs < 1;
}

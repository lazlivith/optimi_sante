/**
 * La conversion d'un prix de référence vers une devise d'affichage.
 *
 * <p>La même règle que le serveur : multiplier par le taux, puis remonter au palier supérieur.
 * Elle est dupliquée ici pour que le prix change instantanément quand le visiteur change de
 * devise, sans aller-retour réseau sur chaque vignette du catalogue. <b>Ce qui fait foi reste
 * le serveur</b> : le montant encaissé est recalculé au moment de payer.</p>
 */

/** Une devise d'affichage et sa règle de conversion, telles que le serveur les publie. */
export interface DeviseAffichage {
  code: string;
  /** Combien d'unités de cette devise valent un euro. */
  taux: number;
  /** Multiple auquel le montant converti remonte. */
  palierArrondi: number;
  decimales: number;
  /** Vrai pour la devise correspondant au pays du visiteur. */
  suggeree: boolean;
}

/** La devise de référence, celle dans laquelle les prix sont stockés. */
export const EURO: DeviseAffichage = {
  code: 'EUR', taux: 1, palierArrondi: 0.01, decimales: 2, suggeree: false,
};

/**
 * Convertit un prix en euros vers la devise donnée.
 *
 * L'arrondi remonte toujours : on n'affiche jamais moins que le prix de référence. La valeur
 * est d'abord ramenée à six décimales, car 500 × 655,957 vaut 327978,49999999994 en virgule
 * flottante — et un plafond appliqué tel quel ferait remonter d'un palier entier pour rien.
 */
export function convertir(montantEuros: number, devise: DeviseAffichage): number {
  if (devise.code === EURO.code || devise.taux === 1) return montantEuros;
  const converti = Math.round(montantEuros * devise.taux * 1e6) / 1e6;
  const palier = devise.palierArrondi > 0 ? devise.palierArrondi : 1;
  return Math.ceil(converti / palier) * palier;
}

/**
 * Le prix tel qu'il s'écrit : « 49,90 € », « 328 000 F CFA ».
 *
 * Le nombre de décimales suit la devise — le franc CFA n'en a pas, et « 328 000,00 » annoncerait
 * une subdivision qui n'existe pas.
 */
export function formaterPrix(montantEuros: number, devise: DeviseAffichage): string {
  const valeur = convertir(montantEuros, devise);
  try {
    return new Intl.NumberFormat('fr-FR', {
      style: 'currency',
      currency: devise.code,
      minimumFractionDigits: devise.decimales,
      maximumFractionDigits: devise.decimales,
    }).format(valeur);
  } catch {
    // Devise inconnue d'Intl : plutot que de ne rien afficher, on ecrit le code apres le
    // montant. Un prix lisible vaut mieux qu'un prix absent.
    return `${valeur.toLocaleString('fr-FR', {
      minimumFractionDigits: devise.decimales,
      maximumFractionDigits: devise.decimales,
    })} ${devise.code}`;
  }
}

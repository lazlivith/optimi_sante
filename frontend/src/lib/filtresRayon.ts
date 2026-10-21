/**
 * Les filtres d'un rayon, et leur présence dans l'adresse.
 *
 * <p><b>Pourquoi l'adresse et pas un état de composant.</b> Un rayon filtré se partage, se
 * met en favori, et se retrouve quand on revient d'une fiche produit. Un état gardé dans le
 * composant disparaît au premier aller-retour : le visiteur qui ouvre un produit puis revient
 * perd ses filtres et doit tout refaire. C'est le moment précis où il s'en va.</p>
 */

export type TriRayon = 'pertinence' | 'prix-croissant' | 'prix-decroissant' | 'nouveautes';

export interface FiltresRayon {
  prixMin?: number;
  prixMax?: number;
  /** `true` : seulement sur devis. `false` : seulement en ligne. Absent : les deux. */
  surDevis?: boolean;
  promo?: boolean;
  tri: TriRayon;
}

export const TRIS: { valeur: TriRayon; libelle: string }[] = [
  { valeur: 'pertinence', libelle: 'Pertinence' },
  { valeur: 'prix-croissant', libelle: 'Prix croissant' },
  { valeur: 'prix-decroissant', libelle: 'Prix décroissant' },
  { valeur: 'nouveautes', libelle: 'Nouveautés' },
];

/**
 * Ce que « sur devis » veut dire, en un seul endroit.
 *
 * <p>Ce texte explique pourquoi un prix manque. Le dupliquer dans la colonne de filtres et
 * dans la fiche produit garantirait qu'il finisse par dire deux choses différentes du même
 * mécanisme. Il passera dans l'administration quand l'écran existera — d'ici là, il vit ici
 * et nulle part ailleurs.</p>
 */
export const TEXTE_SUR_DEVIS =
  'Les produits « sur devis » sont chiffrés selon la quantité et le pays de livraison. '
  + 'Faites votre demande, nous vous répondons avec un prix et un délai.';

const TRIS_CONNUS = new Set<string>(TRIS.map(t => t.valeur));

/** Un nombre positif, ou rien : une saisie illisible vaut une absence de filtre. */
function nombre(valeur: string | null): number | undefined {
  if (valeur === null || valeur.trim() === '') return undefined;
  const n = Number(valeur);
  return Number.isFinite(n) && n >= 0 ? n : undefined;
}

export function lireFiltres(params: URLSearchParams): FiltresRayon {
  const tri = params.get('tri');
  const devis = params.get('devis');
  return {
    prixMin: nombre(params.get('prixMin')),
    prixMax: nombre(params.get('prixMax')),
    surDevis: devis === 'oui' ? true : devis === 'non' ? false : undefined,
    promo: params.get('promo') === 'oui' ? true : undefined,
    // Un tri inconnu — adresse bricolée, ancien favori — retombe sur l'ordre naturel plutôt
    // que de faire échouer la page. Le serveur, lui, le refuse ; ici on affiche.
    tri: tri && TRIS_CONNUS.has(tri) ? (tri as TriRayon) : 'pertinence',
  };
}

/**
 * Les filtres, écrits dans l'adresse.
 *
 * <p>Seul ce qui est posé y figure : une adresse qui porterait tous les critères à vide
 * serait illisible et impossible à partager de tête.</p>
 */
export function ecrireFiltres(f: FiltresRayon, page: number): URLSearchParams {
  const p = new URLSearchParams();
  if (f.prixMin !== undefined) p.set('prixMin', String(f.prixMin));
  if (f.prixMax !== undefined) p.set('prixMax', String(f.prixMax));
  if (f.surDevis !== undefined) p.set('devis', f.surDevis ? 'oui' : 'non');
  if (f.promo) p.set('promo', 'oui');
  if (f.tri !== 'pertinence') p.set('tri', f.tri);
  if (page > 0) p.set('page', String(page + 1));
  return p;
}

export const FILTRES_VIDES: FiltresRayon = { tri: 'pertinence' };

/** Un filtre est-il posé ? Le tri n'en est pas un : il ne retire aucun produit. */
export function auMoinsUnFiltre(f: FiltresRayon): boolean {
  return f.prixMin !== undefined || f.prixMax !== undefined
      || f.surDevis !== undefined || f.promo === true;
}

/** Les étiquettes à afficher au-dessus de la grille, chacune effaçable. */
export function etiquettes(f: FiltresRayon): { cle: keyof FiltresRayon; texte: string }[] {
  const liste: { cle: keyof FiltresRayon; texte: string }[] = [];
  if (f.prixMin !== undefined) liste.push({ cle: 'prixMin', texte: `À partir de ${f.prixMin} €` });
  if (f.prixMax !== undefined) liste.push({ cle: 'prixMax', texte: `Jusqu'à ${f.prixMax} €` });
  if (f.surDevis !== undefined) {
    liste.push({ cle: 'surDevis', texte: f.surDevis ? 'Sur devis' : 'Achat en ligne' });
  }
  if (f.promo) liste.push({ cle: 'promo', texte: 'En promotion' });
  return liste;
}

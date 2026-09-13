/**
 * Sépare une description produit en texte courant et caractéristiques techniques.
 *
 * <p><b>Pourquoi ce module existe.</b> Le catalogue ne porte aucun champ structuré pour les
 * caractéristiques : tout est dans `description`. Mais 668 des 1 459 fiches — 46 % — y logent
 * déjà un vrai tableau, saisi sur deux lignes :</p>
 *
 * <pre>
 *   Coloris
 *    : Noir
 *   Taille
 *    : M
 * </pre>
 *
 * <p>Affichées telles quelles, ces paires apparaissent au milieu du texte comme des lignes
 * orphelines. Extraites, elles donnent la fiche technique que les maquettes demandent, sans
 * qu'on ait rien inventé.</p>
 *
 * <p><b>Liste blanche, et non détection.</b> Tout le catalogue n'emploie que quinze libellés,
 * relevés un par un. Une règle générale — « une ligne courte suivie d'un deux-points » —
 * happait 24 % des lignes du catalogue, dont des phrases entières. Une liste close ne se
 * trompe pas : ce qu'elle ne connaît pas reste dans le texte, à sa place.</p>
 *
 * <p>Le jour où le produit portera de vraies caractéristiques en base, ce module disparaît.
 * D'ici là, il lit ce qui existe.</p>
 */

/**
 * Les quinze libellés du catalogue, et leur forme développée.
 *
 * <p>Les clés sont en minuscules : « Cond. », « Cond » et « cond. » désignent la même chose,
 * et la casse de saisie ne doit pas décider de ce qui s'affiche. Les abréviations sont
 * développées — « Dim. » n'apprend rien à un acheteur, « Dimensions » si.</p>
 */
const LIBELLES: Record<string, string> = {
  'dim.': 'Dimensions',
  'cond.': 'Conditionnement',
  'cond': 'Conditionnement',
  'cont.': 'Contenance',
  'contenance': 'Contenance',
  'coloris': 'Coloris',
  'taille': 'Taille',
  't. de taille': 'Tour de taille',
  'poids': 'Poids',
  'avec brassard': 'Brassard',
  'brassard': 'Brassard',
  'charge max.': 'Charge maximale',
  'charge max': 'Charge maximale',
  'charge supportée': 'Charge supportée',
};

export interface Caracteristique {
  libelle: string;
  valeur: string;
}

export interface FicheProduit {
  /** Le texte courant, découpé aux lignes vides. Les sauts de ligne internes sont conservés. */
  paragraphes: string[];
  /** Les paires reconnues, dans l'ordre où la fiche les donne. */
  caracteristiques: Caracteristique[];
}

export function lireFiche(description: string | null | undefined): FicheProduit {
  if (!description) return { paragraphes: [], caracteristiques: [] };

  const lignes = description.split(/\r?\n/);
  const caracteristiques: Caracteristique[] = [];
  const restant: string[] = [];

  for (let i = 0; i < lignes.length; i++) {
    const libelle = LIBELLES[lignes[i].trim().toLowerCase()];
    const suivante = lignes[i + 1]?.trim();

    if (libelle && suivante?.startsWith(':')) {
      const valeur = suivante.replace(/^:+/, '').trim();
      if (valeur) {
        caracteristiques.push({ libelle, valeur });
        // On saute la ligne de valeur : sans cela elle resterait dans le texte sous la
        // forme d'un « : Noir » sans sujet.
        i++;
        continue;
      }
    }
    restant.push(lignes[i]);
  }

  const paragraphes = restant
    .join('\n')
    .split(/\n\s*\n/)
    .map((p) => p.trim())
    .filter(Boolean);

  return { paragraphes, caracteristiques };
}

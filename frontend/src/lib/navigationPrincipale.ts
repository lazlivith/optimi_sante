/**
 * La barre de navigation principale — une seule source, deux affichages.
 *
 * <p><b>Pourquoi une configuration et pas deux listes de liens.</b> L'ordinateur et le
 * téléphone montraient la même barre écrite deux fois, dans `Navbar.tsx` et dans
 * `MenuMobile.tsx`. Elles avaient déjà divergé : l'ordre n'était pas le même, et
 * « Blog & événements » n'existait que sur le téléphone. Une entrée ajoutée d'un côté
 * manquait de l'autre, sans que rien ne le signale.</p>
 *
 * <p><b>L'ordre est une décision, pas un hasard.</b> Accueil, puis Catalogue — le négoce est
 * le cœur de l'activité marchande et doit être atteignable au premier coup d'œil. Nos
 * Services ensuite, parce que c'est là qu'on cherche ce qui entoure un achat ou une
 * formation. Devenir Partenaire s'adresse à une minorité et vient après. Promotions ferme la
 * marche, et porte le seul badge de la barre.</p>
 */

export interface EntreeNavigation {
  /** Adresse de destination. */
  to: string;
  libelle: string;
  /**
   * Badge affiché à droite du libellé.
   *
   * <p><b>Un seul dans toute la barre.</b> Un badge attire l'œil parce qu'il est seul ; deux
   * badges n'en font aucun. Le test de l'ordre vérifie cette unicité — ce n'est pas une
   * préférence esthétique mais la condition de son efficacité.</p>
   */
  badge?: string;
  /** Vrai pour l'entrée « Nos Services », rendue par un panneau déroulant et non un lien. */
  panneauServices?: boolean;
  /**
   * Entrée affichée seulement dans le menu du téléphone.
   *
   * <p>Le tiroir mobile peut porter plus d'entrées que la barre, qui doit tenir sur une
   * ligne. « Blog & événements » n'a jamais figuré sur l'ordinateur ; le retirer du
   * téléphone supprimerait un chemin existant sans raison.</p>
   */
  surMobileSeulement?: boolean;
}

export const NAVIGATION_PRINCIPALE: EntreeNavigation[] = [
  { to: '/', libelle: 'Accueil' },
  { to: '/catalog', libelle: 'Catalogue' },
  { to: '/services', libelle: 'Nos Services', panneauServices: true },
  { to: '/formations', libelle: 'Formations Médicales' },
  { to: '/devenir-partenaire', libelle: 'Devenir Partenaire' },
  { to: '/catalog?promo=true', libelle: 'Promotions', badge: 'New' },
  { to: '/blog', libelle: 'Blog & événements', surMobileSeulement: true },
];

/** Ce que la barre de l'ordinateur affiche : tout sauf les entrées réservées au téléphone. */
export const NAVIGATION_ORDINATEUR = NAVIGATION_PRINCIPALE.filter(e => !e.surMobileSeulement);

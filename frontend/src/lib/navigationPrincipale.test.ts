import { describe, expect, it } from 'vitest';
import { NAVIGATION_PRINCIPALE, NAVIGATION_ORDINATEUR } from './navigationPrincipale';
import { GROUPES_SERVICES } from './servicesMenu';

/**
 * L'ordre et les badges de la barre de navigation.
 *
 * <p><b>Pourquoi figer un ordre dans un test.</b> Il portait une décision commerciale — le
 * Catalogue en deuxième position parce que le négoce est le cœur de l'activité marchande — et
 * cette décision ne se devine pas en lisant le code. Une refonte de la barre la perdrait sans
 * que personne ne s'en aperçoive, et il faudrait la reprendre depuis le début.</p>
 *
 * <p>L'ordinateur et le téléphone lisent la MÊME configuration : c'est ce qu'on vérifie ici,
 * et c'est ce qui manquait quand les deux barres divergeaient.</p>
 */
describe('barre de navigation principale', () => {
  it('affiche les six entrées dans l’ordre décidé, sur ordinateur', () => {
    expect(NAVIGATION_ORDINATEUR.map(e => e.libelle)).toEqual([
      'Accueil',
      'Catalogue',
      'Nos Services',
      'Formations Médicales',
      'Devenir Partenaire',
      'Promotions',
    ]);
  });

  it('garde le même ordre sur téléphone, en ajoutant le blog à la fin', () => {
    // Le tiroir peut porter plus d'entrées que la barre, qui tient sur une ligne. Ce qu'il
    // ne doit pas faire, c'est les présenter dans un autre ordre — c'était le cas avant.
    expect(NAVIGATION_PRINCIPALE.map(e => e.libelle)).toEqual([
      ...NAVIGATION_ORDINATEUR.map(e => e.libelle),
      'Blog & événements',
    ]);
  });

  it('ne porte qu’UN SEUL badge dans toute la barre', () => {
    // Un badge attire l'œil parce qu'il est seul. Deux badges n'en font aucun : c'est la
    // raison d'être de ce test, et non une préférence de présentation.
    const avecBadge = NAVIGATION_PRINCIPALE.filter(e => e.badge);
    expect(avecBadge).toHaveLength(1);
    expect(avecBadge[0].libelle).toBe('Promotions');
    expect(avecBadge[0].badge).toBe('New');
  });

  it('désigne « Nos Services » comme panneau, et lui seul', () => {
    const panneaux = NAVIGATION_PRINCIPALE.filter(e => e.panneauServices);
    expect(panneaux).toHaveLength(1);
    expect(panneaux[0].libelle).toBe('Nos Services');
  });

  it('mène chaque entrée quelque part', () => {
    NAVIGATION_PRINCIPALE.forEach(e => {
      expect(e.to, `l’entrée « ${e.libelle} » n’a pas d’adresse`).toMatch(/^\//);
    });
  });
});

describe('sous-menu « Nos Services »', () => {
  const entrees = GROUPES_SERVICES.flatMap(g => g.entrees);

  it('contient « Exercer en France », signalé comme nouveau', () => {
    const exercer = entrees.find(e => e.to === '/exercer-en-france');
    expect(exercer, 'l’entrée « Exercer en France » a disparu du menu').toBeDefined();
    expect(exercer!.nouveau).toBe(true);
  });

  it('ne signale qu’une seule nouveauté à la fois', () => {
    // Même règle que pour la barre : la pastille ne vaut que par sa rareté.
    expect(entrees.filter(e => e.nouveau)).toHaveLength(1);
  });
});

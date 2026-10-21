import { render, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ExercerEnFrancePage } from './ExercerEnFrancePage';
import { exercerEnFranceService, type PageExercerEnFrance } from '../api/exercerEnFranceService';

vi.mock('../api/exercerEnFranceService', () => ({
  exercerEnFranceService: { page: vi.fn() },
}));

/**
 * La page ne renvoie vers AUCUN site extérieur.
 *
 * <p><b>Pourquoi un test et pas seulement une relecture.</b> Optimi Santé est une agence
 * privée qui prend en charge le dossier du candidat jusqu'à son dépôt. Un lien sortant posé
 * au milieu d'une page commerciale entretient la confusion sur ce que nous sommes, et fait
 * partir vers un tiers celui qu'on accompagne. Ce sont des liens que l'administration peut
 * saisir, et que la page pourrait réafficher à la première évolution : la règle doit tenir
 * toute seule.</p>
 *
 * <p>Les adresses sont servies par l'API — elles restent en base pour notre suivi interne.
 * Ce test les injecte donc EXPRÈS, et vérifie qu'aucune ne devient cliquable.</p>
 */

const AVEC_LIENS: PageExercerEnFrance = {
  sections: [
    { cle: 'bandeau_non_officiel', surTitre: null,
      titre: "Optimi Santé n'est pas un organisme officiel", contenu: 'Un texte.' },
    { cle: 'evc', surTitre: null, titre: 'Les EVC', contenu: 'Un texte.' },
    { cle: 'aep', surTitre: null, titre: "L'AEP",
      contenu: '## Renouvellement\nUn texte sur le renouvellement.' },
    { cle: 'cae', surTitre: null, titre: 'La commission', contenu: 'Un texte.' },
    { cle: 'autres_voies', surTitre: null, titre: 'Autres voies',
      contenu: '## Fellowship\nUn texte.\n## Praticiens réfugiés\nUn texte.' },
  ],
  periodes: [{
    libelle: '1re période 2026', dateDebut: null, dateFin: null,
    specialitesNonOuvertes: null,
    sourceUrl: 'https://www.cng.sante.fr/session-evc-2026', statut: 'a_venir',
  }],
  // Toutes les adresses officielles, telles que la base les porte aujourd'hui.
  liens: [
    { cle: 'site_cng', libelle: 'Site officiel du CNG', url: 'https://www.cng.sante.fr/x' },
    { cle: 'session_evc', libelle: 'Prochaine session EVC', url: 'https://www.cng.sante.fr/session-evc-2026' },
    { cle: 'calendrier_commissions', libelle: 'Voir le calendrier officiel', url: 'https://www.cng.sante.fr/cae' },
    { cle: 'fellowship', libelle: 'En savoir plus', url: 'https://www.cng.sante.fr/fellowship' },
    { cle: 'refugies', libelle: 'Dispositions spécifiques', url: 'https://www.cng.sante.fr/session-evc-2026' },
    { cle: 'formulaire_renouvellement_aep', libelle: 'Formulaire', url: 'https://demarche.numerique.gouv.fr/y' },
  ],
  specialites: [],
  offres: [],
  misAJourLe: null,
  version: 1,
  calendrierActualiseLe: '2026-09-03',
};

describe('aucun lien vers un site extérieur', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(exercerEnFranceService.page).mockResolvedValue(AVEC_LIENS);
  });

  it('ne rend aucune ancre vers un domaine extérieur', async () => {
    const { container } = render(<MemoryRouter><ExercerEnFrancePage /></MemoryRouter>);
    await waitFor(() => expect(container.querySelectorAll('section').length).toBeGreaterThan(0));

    const sortants = Array.from(container.querySelectorAll('a[href]'))
      .map(a => a.getAttribute('href') ?? '')
      .filter(h => /^https?:\/\//i.test(h));

    expect(sortants, `liens sortants trouvés : ${sortants.join(', ')}`).toHaveLength(0);
  });

  it("n'ouvre aucun onglet extérieur", async () => {
    const { container } = render(<MemoryRouter><ExercerEnFrancePage /></MemoryRouter>);
    await waitFor(() => expect(container.querySelectorAll('section').length).toBeGreaterThan(0));

    expect(container.querySelectorAll('[target="_blank"]')).toHaveLength(0);
  });

  it("n'affiche nulle part une adresse de site officiel", async () => {
    const { container } = render(<MemoryRouter><ExercerEnFrancePage /></MemoryRouter>);
    await waitFor(() => expect(container.querySelectorAll('section').length).toBeGreaterThan(0));

    // Ni en lien, ni en texte brut : une adresse recopiée à la main serait le même aveu.
    expect(container.innerHTML).not.toContain('cng.sante.fr');
    expect(container.innerHTML).not.toContain('demarche.numerique.gouv.fr/y');
  });

  it('cite la source sans la rendre cliquable', async () => {
    const { container } = render(<MemoryRouter><ExercerEnFrancePage /></MemoryRouter>);
    await waitFor(() => expect(container.querySelectorAll('section').length).toBeGreaterThan(0));

    // L'information reste vérifiable : on dit d'où elle vient et quand on l'a constatée.
    expect(container.textContent).toContain('Source : Centre national de gestion');
    expect(container.textContent).toContain('constaté le');
  });

  it('conserve le bandeau « pas un organisme officiel »', async () => {
    const { container } = render(<MemoryRouter><ExercerEnFrancePage /></MemoryRouter>);
    await waitFor(() => expect(container.querySelectorAll('section').length).toBeGreaterThan(0));

    // Retirer les liens ne doit jamais emporter cette mention : elle dit ce que nous sommes.
    expect(container.textContent).toContain("Optimi Santé n'est pas un organisme officiel");
  });
});

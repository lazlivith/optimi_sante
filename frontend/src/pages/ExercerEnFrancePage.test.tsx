import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ExercerEnFrancePage } from './ExercerEnFrancePage';
import { exercerEnFranceService, type PageExercerEnFrance } from '../api/exercerEnFranceService';

vi.mock('../api/exercerEnFranceService', () => ({
  exercerEnFranceService: { page: vi.fn() },
}));

/** Ce qu'un administrateur mal intentionné — ou un presse-papier — pourrait saisir. */
const CHARGE_TITRE = '<script>alert(1)</script>';
const CHARGE_IMAGE = '<img src=x onerror=alert(1)>';

const page = (sections: PageExercerEnFrance['sections']): PageExercerEnFrance => ({
  sections,
  periodes: [],
  liens: [],
  specialites: [],
  offres: [],
  misAJourLe: null,
  version: 1,
  calendrierActualiseLe: null,
});

function afficher() {
  return render(<MemoryRouter><ExercerEnFrancePage /></MemoryRouter>);
}

describe('contenu rédigé par l’administration', () => {
  beforeEach(() => vi.clearAllMocks());

  it('affiche le HTML saisi comme du TEXTE, sans l’interpréter', async () => {
    // Les textes de cette page sont ecrits dans l'administration et rendus a des visiteurs.
    // Interpretes, un « <img src=x onerror=…> » colle dans un champ s'executerait chez
    // chacun d'eux, dans notre domaine : c'est l'injection de script la plus banale, et elle
    // passe par un formulaire plutot que par une faille technique.
    vi.mocked(exercerEnFranceService.page).mockResolvedValue(page([{
      cle: 'evc',
      surTitre: 'ÉTAPE 1',
      titre: `Les EVC ${CHARGE_TITRE}`,
      contenu: `Un texte ${CHARGE_IMAGE} au milieu.`,
    }]));

    const { container } = afficher();
    await waitFor(() => expect(screen.getByRole('heading', { level: 1 })).toBeInTheDocument());

    // 1. Le texte s'affiche LITTERALEMENT : la preuve qu'il a ete echappe, et pas seulement
    //    que rien ne s'est passe.
    expect(container.textContent).toContain(CHARGE_TITRE);
    expect(container.textContent).toContain(CHARGE_IMAGE);

    // 2. Aucune balise n'a ete creee a partir de ce texte.
    expect(container.querySelectorAll('script')).toHaveLength(0);
    expect(container.querySelectorAll('img[onerror]')).toHaveLength(0);
  });

  it('n’interprète pas davantage le HTML d’un encadré ou d’une mention', async () => {
    // Le decoupage sur « ## » et « > » construit des elements a partir du texte : c'est la
    // qu'on serait tente d'injecter du HTML pour la mise en forme.
    vi.mocked(exercerEnFranceService.page).mockResolvedValue(page([{
      cle: 'dossier',
      surTitre: null,
      titre: 'Bien constituer son dossier',
      contenu: `Introduction.\n## Encadré ${CHARGE_TITRE}\nContenu ${CHARGE_IMAGE}.\n`
             + `> Mention ${CHARGE_IMAGE} finale.`,
    }]));

    const { container } = afficher();
    await waitFor(() => expect(screen.getByText(/Bien constituer/)).toBeInTheDocument());

    expect(container.querySelectorAll('script')).toHaveLength(0);
    expect(container.querySelectorAll('img')).toHaveLength(0);
    expect(container.textContent).toContain(CHARGE_IMAGE);
  });

  it('ne rend aucun lien à partir du texte d’une section', async () => {
    // Les seuls liens de la page viennent de « liens », dont les adresses sont validees au
    // serveur. Si le texte d'une section pouvait en produire un, cette validation serait
    // contournee par le champ d'a cote.
    vi.mocked(exercerEnFranceService.page).mockResolvedValue(page([{
      cle: 'evc',
      surTitre: null,
      titre: 'Les EVC',
      contenu: '<a href="javascript:alert(1)">Cliquez ici</a> et https://exemple.fr',
    }]));

    const { container } = afficher();
    await waitFor(() => expect(screen.getByText(/Les EVC/)).toBeInTheDocument());

    const liens = [...container.querySelectorAll('a')]
      .map((a) => a.getAttribute('href') ?? '');
    expect(liens.some((h) => h.toLowerCase().includes('javascript'))).toBe(false);
  });
});

import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ExercerEnFrancePage } from './ExercerEnFrancePage';
import { exercerEnFranceService, type PageExercerEnFrance } from '../api/exercerEnFranceService';
import { demandeAccompagnementService } from '../api/demandeAccompagnementService';

vi.mock('../api/exercerEnFranceService', () => ({
  exercerEnFranceService: { page: vi.fn() },
}));
vi.mock('../api/demandeAccompagnementService', () => ({
  demandeAccompagnementService: { deposer: vi.fn() },
}));

/**
 * Ce que chaque bouton ENVOIE, et non ce qu'il affiche.
 *
 * <p><b>Pourquoi la charge utile et pas le rendu.</b> Des demandes sont arrivées en
 * production avec une offre vide, et la fenêtre affichait pourtant le bon titre. Un test de
 * rendu les aurait toutes déclarées bonnes : ce qui part sur le réseau est la seule chose qui
 * décide de ce qu'on enregistre et de ce que le praticien lira dans son accusé.</p>
 */

const OFFRES: PageExercerEnFrance['offres'] = [
  { cle: 'preparation_evc', surTitre: 'AVANT LES EVC', titre: 'Orientation EVC',
    points: ['Vérification'], prix: 89, prixBarre: null, badge: null,
    devise: 'EUR', libelleBouton: 'Être orienté' },
  { cle: 'dossier_demarches', surTitre: null, titre: 'Dossier & démarches',
    points: ['Relecture'], prix: 450, prixBarre: null, badge: null,
    devise: 'EUR', libelleBouton: 'Faire relire mon dossier' },
  { cle: 'mobilite_installation', surTitre: null, titre: 'Mobilité & installation',
    points: ['Visa'], prix: 650, prixBarre: null, badge: null,
    devise: 'EUR', libelleBouton: 'Préparer mon arrivée' },
  { cle: 'pack_dossier_mobilite', surTitre: null, titre: 'Pack Dossier + Mobilité',
    points: ['Tout'], prix: 990, prixBarre: 1100, badge: 'Le plus complet',
    devise: 'EUR', libelleBouton: 'Choisir le pack' },
];

const PAGE: PageExercerEnFrance = {
  sections: [
    { cle: 'accompagnements', surTitre: null, titre: 'Nos accompagnements', contenu: null },
    { cle: 'evc', surTitre: null, titre: 'Les EVC', contenu: 'Un texte.' },
    { cle: 'aep', surTitre: null, titre: "L'attestation provisoire", contenu: 'Un texte.' },
  ],
  periodes: [{
    libelle: '1re période 2026', dateDebut: null, dateFin: null,
    specialitesNonOuvertes: null, sourceUrl: 'https://exemple.fr', statut: 'a_venir',
  }],
  liens: [],
  specialites: [],
  offres: OFFRES,
  misAJourLe: null,
  version: 1,
  calendrierActualiseLe: '2026-09-03',
};

function afficher() {
  return render(<MemoryRouter><ExercerEnFrancePage /></MemoryRouter>);
}

/** Ouvre la fenêtre depuis ce bouton, remplit le minimum, envoie, et rend la charge utile. */
async function envoyerDepuis(libelleBouton: RegExp) {
  const utilisateur = userEvent.setup();
  afficher();
  await waitFor(() => expect(screen.getAllByRole('button').length).toBeGreaterThan(0));

  const boutons = await screen.findAllByRole('button', { name: libelleBouton });
  await utilisateur.click(boutons[0]);

  await screen.findByRole('dialog');
  await utilisateur.type(screen.getByLabelText(/Nom complet/), 'Dr Nkounkou');
  await utilisateur.type(screen.getByLabelText(/Adresse e-mail/), 'n@exemple.cg');
  await utilisateur.type(screen.getByLabelText(/Pays de résidence/), 'Congo');
  await utilisateur.click(screen.getByRole('checkbox'));
  await utilisateur.click(
    screen.getByRole('button', { name: /Envoyer ma demande|Me prévenir/ }));

  await waitFor(() => expect(demandeAccompagnementService.deposer).toHaveBeenCalled());
  return vi.mocked(demandeAccompagnementService.deposer).mock.calls[0][0];
}

describe('la clé de l’offre réellement envoyée', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(exercerEnFranceService.page).mockResolvedValue(PAGE);
    vi.mocked(demandeAccompagnementService.deposer)
      .mockResolvedValue({ id: 'id-1', message: 'Enregistrée.' });
  });

  it.each([
    ['Être orienté', 'preparation_evc'],
    ['Faire relire mon dossier', 'dossier_demarches'],
    ['Préparer mon arrivée', 'mobilite_installation'],
    ['Choisir le pack', 'pack_dossier_mobilite'],
  ])('la carte « %s » envoie %s', async (bouton, cleAttendue) => {
    const charge = await envoyerDepuis(new RegExp(bouton));
    expect(charge.offreCle).toBe(cleAttendue);
  });

  it.each([
    ['Être accompagné'],
    ['Prendre rendez-vous'],
  ])('le bouton général « %s » n’envoie AUCUNE offre', async (bouton) => {
    // Et surtout pas une chaîne vide : le serveur écartait le cas « null » et laissait
    // passer « "" », ce qui a produit « votre demande concernant . » dans un accusé.
    const charge = await envoyerDepuis(new RegExp(bouton));
    expect(charge.offreCle).toBeUndefined();
    expect(charge.offreCle).not.toBe('');
  });

  it('le bouton d’alerte d’une période envoie alerte_aep', async () => {
    const charge = await envoyerDepuis(/alerté/i);
    expect(charge.offreCle).toBe('alerte_aep');
  });

});

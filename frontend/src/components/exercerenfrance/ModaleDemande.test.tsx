import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ModaleDemande } from './ModaleDemande';
import { demandeAccompagnementService } from '../../api/demandeAccompagnementService';

vi.mock('../../api/demandeAccompagnementService', () => ({
  demandeAccompagnementService: { deposer: vi.fn() },
}));

const SPECIALITES = ['Néphrologie', 'Oncologie'];

function afficher(offreCle: string, offreTitre = 'Préparation EVC') {
  const onFermer = vi.fn();
  render(
    <MemoryRouter>
      <ModaleDemande offreCle={offreCle} offreTitre={offreTitre}
                     specialites={SPECIALITES} onFermer={onFermer} />
    </MemoryRouter>,
  );
  return { onFermer };
}

async function remplirLeMinimum(utilisateur: ReturnType<typeof userEvent.setup>) {
  await utilisateur.type(screen.getByLabelText(/Nom complet/), 'Dr Nkounkou');
  await utilisateur.type(screen.getByLabelText(/Adresse e-mail/), 'n@exemple.cg');
  await utilisateur.click(screen.getByRole('checkbox'));
}

describe('fenêtre de demande', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(demandeAccompagnementService.deposer)
      .mockResolvedValue({ id: 'abc', message: 'Votre demande est enregistrée.' });
  });

  it('demande l’étape et le message pour un accompagnement', () => {
    afficher('preparation_evc');
    expect(screen.getByLabelText(/Où en êtes-vous/)).toBeInTheDocument();
    expect(screen.getByLabelText(/Votre message/)).toBeInTheDocument();
  });

  it('les retire pour une alerte d’ouverture', () => {
    // Un praticien qui veut seulement être prévenu d'une date n'a aucune raison de raconter
    // sa situation : les lui réclamer ferait abandonner ceux qui n'ont rien à dire.
    afficher('alerte_aep', '2nde période 2026');
    expect(screen.queryByLabelText(/Où en êtes-vous/)).not.toBeInTheDocument();
    expect(screen.queryByLabelText(/Votre message/)).not.toBeInTheDocument();
    // Mais elle dit ce qu'on fera de la demande.
    expect(screen.getByText(/Centre National de Gestion publiera les dates/)).toBeInTheDocument();
  });

  it('refuse l’envoi sans consentement, et le dit sur la case', async () => {
    const utilisateur = userEvent.setup();
    afficher('preparation_evc');
    await utilisateur.type(screen.getByLabelText(/Nom complet/), 'Dr Nkounkou');
    await utilisateur.type(screen.getByLabelText(/Adresse e-mail/), 'n@exemple.cg');
    await utilisateur.click(screen.getByRole('button', { name: /Envoyer ma demande/ }));

    expect(await screen.findByText(/consentement est nécessaire/)).toBeInTheDocument();
    expect(demandeAccompagnementService.deposer).not.toHaveBeenCalled();
  });

  it('signale tous les champs fautifs d’un coup', async () => {
    const utilisateur = userEvent.setup();
    afficher('preparation_evc');
    await utilisateur.click(screen.getByRole('button', { name: /Envoyer ma demande/ }));

    // Trois alertes : nom, adresse, consentement. Les rendre une par une ferait recommencer
    // le praticien autant de fois qu'il a d'erreurs.
    await waitFor(() => expect(screen.getAllByRole('alert')).toHaveLength(3));
  });

  it('transmet la clé de l’offre et le délai de remplissage', async () => {
    const utilisateur = userEvent.setup();
    afficher('alerte_aep', '2nde période 2026');
    await remplirLeMinimum(utilisateur);
    await utilisateur.click(screen.getByRole('button', { name: /Me prévenir/ }));

    await waitFor(() => expect(demandeAccompagnementService.deposer).toHaveBeenCalled());
    const envoye = vi.mocked(demandeAccompagnementService.deposer).mock.calls[0][0];
    // La clé rattache la demande : c'est elle qui permettra de retrouver qui prévenir le
    // jour où le CNG publiera ses dates.
    expect(envoye.offreCle).toBe('alerte_aep');
    expect(envoye.consentementRgpd).toBe(true);
    expect(typeof envoye.ouvertDepuisMs).toBe('number');
    // Le champ piège part vide : un humain ne le voit pas, donc ne le remplit pas.
    expect(envoye.piege).toBe('');
  });

  it('le champ piège est invisible ET inatteignable au clavier', () => {
    afficher('preparation_evc');
    const piege = document.getElementById('site-web') as HTMLInputElement;
    expect(piege).toBeTruthy();
    expect(piege.tabIndex).toBe(-1);
    // aria-hidden autant que la mise en page : piéger un lecteur d'écran reviendrait à
    // écarter les aveugles au lieu des robots.
    expect(piege.closest('[aria-hidden="true"]')).not.toBeNull();
  });

  it('confirme sans fermer d’autorité, et le formulaire disparaît', async () => {
    const utilisateur = userEvent.setup();
    const { onFermer } = afficher('preparation_evc');
    await remplirLeMinimum(utilisateur);
    await utilisateur.click(screen.getByRole('button', { name: /Envoyer ma demande/ }));

    expect(await screen.findByText(/Demande enregistrée/)).toBeInTheDocument();
    expect(screen.queryByLabelText(/Nom complet/)).not.toBeInTheDocument();
    // Fermer d'autorité ferait disparaître la confirmation avant qu'on l'ait lue.
    expect(onFermer).not.toHaveBeenCalled();
  });

  it('affiche l’échec sans perdre la saisie', async () => {
    const utilisateur = userEvent.setup();
    vi.mocked(demandeAccompagnementService.deposer).mockRejectedValue(new Error('réseau'));
    afficher('preparation_evc');
    await remplirLeMinimum(utilisateur);
    await utilisateur.click(screen.getByRole('button', { name: /Envoyer ma demande/ }));

    expect(await screen.findByRole('alert')).toHaveTextContent(/pas pu être envoyée/);
    // Ce qui a été tapé reste : refaire saisir après un échec réseau fait abandonner.
    expect(screen.getByLabelText(/Nom complet/)).toHaveValue('Dr Nkounkou');
  });

  it('se ferme à la touche Échap', async () => {
    const utilisateur = userEvent.setup();
    const { onFermer } = afficher('preparation_evc');
    await utilisateur.keyboard('{Escape}');
    expect(onFermer).toHaveBeenCalled();
  });
});

import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { BarreRecherche } from './BarreRecherche';
import { catalogService } from '../../api/catalogService';

vi.mock('../../api/catalogService', () => ({
  catalogService: { getCategories: vi.fn() },
}));

const TENSIOMETRES = {
  id: 'id-tensiometres', name: 'Tensiomètres', slug: 'tensiometres', productCount: 12,
};
const PESAGE = {
  id: 'id-pesage', name: 'Pesage', slug: 'pesage', productCount: 4,
};
/** Un rayon vide : la liste ne doit pas le proposer, il ne mène nulle part. */
const VIDE = {
  id: 'id-vide', name: 'Bilirubinomètres', slug: 'bilirubinometres', productCount: 0,
};

/** Affiche l'adresse courante, pour vérifier où la soumission a mené. */
function Adresse() {
  const { pathname, search } = useLocation();
  return <p data-testid="adresse">{pathname + search}</p>;
}

function afficher(depart = '/') {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[depart]}>
        <BarreRecherche />
        <Adresse />
        <Routes>
          <Route path="/category/:slug" element={<span>rayon</span>} />
          <Route path="*" element={<span>ailleurs</span>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

const adresse = () => screen.getByTestId('adresse').textContent;

/**
 * Attend que les rayons soient arrivés.
 *
 * <p>Attendre le <i>libellé</i> « Catégorie » ne suffit pas : il est là dès le premier
 * rendu, avant la réponse du serveur, et le sélecteur ne porte alors que « Toutes
 * catégories ». C'est l'arrivée d'une option de rayon qui marque la fin du chargement.</p>
 */
const attendreLesRayons = () => waitFor(() =>
  expect(screen.getByRole('option', { name: 'Tensiomètres' })).toBeInTheDocument());

describe('barre de recherche de l’en-tête', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(catalogService.getCategories)
      .mockResolvedValue([VIDE, TENSIOMETRES, PESAGE] as never);
  });

  it('est un formulaire de recherche, avec un libellé pour chaque contrôle', async () => {
    afficher();
    // `role="search"` : c'est ce qui permet d'atteindre la recherche directement, sans
    // parcourir l'en-tête entier.
    expect(screen.getByRole('search')).toBeInTheDocument();
    expect(screen.getByLabelText('Rechercher un produit')).toBeInTheDocument();
    await attendreLesRayons();
    // Le bouton n'affiche son libellé qu'au-dessus de 1 024 px : son nom accessible ne
    // dépend donc pas du texte visible.
    expect(screen.getByRole('button', { name: 'Rechercher' })).toBeInTheDocument();
  });

  it('ne propose que les rayons qui mènent quelque part, par ordre alphabétique', async () => {
    afficher();
    await attendreLesRayons();

    const libelles = screen.getAllByRole('option').map(o => o.textContent);
    expect(libelles).toEqual(['Toutes catégories', 'Pesage', 'Tensiomètres']);
    expect(libelles).not.toContain('Bilirubinomètres');
  });

  it('envoie le terme dans l’adresse, et non dans un état de composant', async () => {
    const utilisateur = userEvent.setup();
    afficher();

    await utilisateur.type(screen.getByLabelText('Rechercher un produit'), 'brassard');
    await utilisateur.click(screen.getByRole('button', { name: 'Rechercher' }));

    expect(adresse()).toBe('/catalog?search=brassard');
  });

  it('envoie le terme ET la catégorie quand les deux sont posés', async () => {
    const utilisateur = userEvent.setup();
    afficher();
    await attendreLesRayons();

    await utilisateur.selectOptions(screen.getByLabelText('Catégorie'), 'id-tensiometres');
    await utilisateur.type(screen.getByLabelText('Rechercher un produit'), 'omron');
    await utilisateur.click(screen.getByRole('button', { name: 'Rechercher' }));

    expect(adresse()).toBe('/catalog?search=omron&category=id-tensiometres');
  });

  it('accepte une catégorie seule, sans terme', async () => {
    const utilisateur = userEvent.setup();
    afficher();
    await attendreLesRayons();

    await utilisateur.selectOptions(screen.getByLabelText('Catégorie'), 'id-pesage');
    await utilisateur.click(screen.getByRole('button', { name: 'Rechercher' }));

    expect(adresse()).toBe('/catalog?category=id-pesage');
  });

  it('se soumet au clavier seul, sans jamais toucher la souris', async () => {
    const utilisateur = userEvent.setup();
    afficher();

    await utilisateur.type(screen.getByLabelText('Rechercher un produit'), 'oxymètre');
    // Tab mène du champ au bouton, Entrée l'actionne. C'est le parcours d'un visiteur
    // qui n'utilise pas de souris, et le critère d'acceptation du lot.
    await utilisateur.tab();
    expect(screen.getByRole('button', { name: 'Rechercher' })).toHaveFocus();
    await utilisateur.keyboard('{Enter}');

    // Le terme accentué voyage encodé : on l'énonce par `URLSearchParams` plutôt que de
    // recopier « oxym%C3%A8tre », qui ne dit rien à la relecture.
    expect(adresse()).toBe(`/catalog?${new URLSearchParams({ search: 'oxymètre' })}`);
  });

  it('ne mène nulle part quand rien n’est saisi', async () => {
    const utilisateur = userEvent.setup();
    afficher();

    await utilisateur.click(screen.getByRole('button', { name: 'Rechercher' }));

    // Toujours sur la page de départ : envoyer un formulaire vide afficherait le catalogue
    // entier en donnant à croire qu'une recherche a eu lieu.
    expect(adresse()).toBe('/');
  });

  it('ignore un terme qui n’est que des espaces', async () => {
    const utilisateur = userEvent.setup();
    afficher();

    await utilisateur.type(screen.getByLabelText('Rechercher un produit'), '   ');
    await utilisateur.click(screen.getByRole('button', { name: 'Rechercher' }));

    expect(adresse()).toBe('/');
  });

  it('s’ouvre sur le rayon en cours quand on est sur sa page', async () => {
    afficher('/category/tensiometres');

    await waitFor(() =>
      expect(screen.getByLabelText('Catégorie')).toHaveValue('id-tensiometres'));
  });

  it('reste sur « Toutes catégories » pour un rayon absent de la liste', async () => {
    // Un rayon sans produit ne figure pas dans la liste. Afficher son nom tout de même
    // demanderait de le rajouter, et le choisir ne ramènerait rien.
    afficher('/category/bilirubinometres');

    await attendreLesRayons();
    expect(screen.getByLabelText('Catégorie')).toHaveValue('');
  });
});

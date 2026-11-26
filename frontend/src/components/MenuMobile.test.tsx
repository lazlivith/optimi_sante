import { createRef } from 'react';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import { MenuMobile } from './MenuMobile';
import { NAVIGATION_PRINCIPALE } from '../lib/navigationPrincipale';

vi.mock('../context/AuthContext', () => ({
  useAuth: () => ({ user: null, isAuthenticated: false }),
}));
vi.mock('../context/CartContext', () => ({
  useCart: () => ({ totalItems: 0 }),
}));

function afficher() {
  render(
    <MemoryRouter>
      <MenuMobile
        ouvert
        fermer={vi.fn()}
        fermerSurLien={vi.fn()}
        panneau={createRef<HTMLElement>()}
      />
    </MemoryRouter>,
  );
}

/**
 * Non-régression du tiroir du téléphone, pendant le lot M1.
 *
 * <p>M1 refait la barre de recherche de l'en-tête. Le tiroir partage avec lui
 * <code>NAVIGATION_PRINCIPALE</code> et l'en-tête de compte et de panier : une barre
 * déplacée ou un import retiré de trop s'y verrait d'abord ici, et sur un écran que
 * personne n'ouvre pendant une relecture de code.</p>
 */
describe('tiroir de navigation du téléphone', () => {
  it('affiche toutes les entrées de la navigation, « Nos Services » comprise', () => {
    afficher();

    // Lues de la même source que la barre de l'ordinateur : si l'une des deux divergeait,
    // ce test le dirait. C'est le défaut qui a justifié `navigationPrincipale.ts`.
    NAVIGATION_PRINCIPALE.forEach((entree) => {
      expect(screen.getByText(entree.libelle)).toBeInTheDocument();
    });
  });

  it('garde l’accès au panier, le tiroir étant plein écran', () => {
    afficher();
    expect(screen.getByRole('link', { name: /panier/i })).toBeInTheDocument();
  });

  it('ne porte PAS de champ de recherche : il est dans l’en-tête', () => {
    afficher();

    // La maquette du téléphone place la recherche dans l'en-tête, visible sans ouvrir le
    // tiroir. En ajouter un second ici donnerait deux champs pour une même recherche.
    expect(screen.queryByRole('search')).not.toBeInTheDocument();
    expect(screen.queryByRole('searchbox')).not.toBeInTheDocument();
  });
});

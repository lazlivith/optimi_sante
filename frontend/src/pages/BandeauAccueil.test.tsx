import { describe, it, expect } from 'vitest';
import { render, fireEvent } from '@testing-library/react';
import { heroSlides, HeroVisuel } from './HomePage';

/**
 * Le bandeau d'accueil ne montre jamais un emplacement vide.
 *
 * <p><b>Ce qu'il faisait.</b> Ses trois diapositives pointaient vers trois adresses Cloudinary
 * qui n'ont jamais été déposées — `default-medical-equipment.jpg`, `stetho.jpg`,
 * `microscope.jpg` — et répondaient 404 depuis l'origine. La page masquait la balise `img` en
 * cas d'erreur, ce qui laissait une colonne vide de 208 px à droite du texte sur la première
 * chose qu'un visiteur voit, et lançait trois requêtes perdues à chaque visite.</p>
 *
 * <p>Les tests portent sur la règle — pas d'adresse, pas de colonne — et non sur les photos :
 * le jour où de vraies photos seront fournies, ils doivent continuer de passer.</p>
 */
describe('le bandeau de la page d’accueil', () => {
  const ADRESSES_MORTES = ['default-medical-equipment', 'stetho.jpg', 'microscope.jpg'];

  it('ne pointe vers aucune des adresses mortes de la migration V9', () => {
    for (const diapositive of heroSlides) {
      for (const morte of ADRESSES_MORTES) {
        expect(diapositive.imageSrc.includes(morte)).toBe(false);
      }
    }
  });

  it('n’affiche aucune balise image quand la diapositive n’a pas de photo', () => {
    // Une balise `img` au `src` vide est précisément ce qui produisait le trou : le navigateur
    // réserve la place, puis n'a rien à y mettre.
    const { container } = render(<HeroVisuel src="" />);
    expect(container.querySelectorAll('img').length).toBe(0);
    expect(container.innerHTML).toBe('');
  });

  it('affiche la photo le jour où une diapositive en porte une', () => {
    // La règle doit rester réversible : remettre une photo ne demande que de renseigner le
    // champ, sans toucher au composant.
    const { container } = render(<HeroVisuel src="https://exemple.test/entrepot.jpg" />);
    const image = container.querySelector('img');
    expect(image?.getAttribute('src')).toBe('https://exemple.test/entrepot.jpg');
  });

  it('retire tout l’emplacement si une photo fournie échoue à charger', () => {
    const { container } = render(<HeroVisuel src="https://exemple.test/absente.jpg" />);
    const image = container.querySelector('img');
    expect(image).not.toBeNull();

    // Le défaut d'origine masquait l'image mais laissait son conteneur de 208 px.
    fireEvent.error(image!);
    expect(container.querySelectorAll('img').length).toBe(0);
    expect(container.innerHTML).toBe('');
  });
});

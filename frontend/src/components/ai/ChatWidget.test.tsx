import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { AssistantText } from './ChatWidget';

/**
 * La bulle de l'assistant affichait littéralement « 1. **Connexion** : » et « (https://… ) ».
 * Ces cas verrouillent le rendu du petit sous-ensemble de Markdown que le modèle emploie.
 */
const afficher = (texte: string) =>
  render(
    <MemoryRouter>
      <AssistantText text={texte} />
    </MemoryRouter>,
  );

describe("rendu des réponses de l'assistant", () => {
  it('met en gras sans laisser les astérisques', () => {
    afficher('Le **tensiomètre Omron** est en stock.');

    expect(screen.getByText('tensiomètre Omron').tagName).toBe('STRONG');
    expect(screen.queryByText(/\*\*/)).toBeNull();
  });

  it('transforme une liste numérotée en vraie liste', () => {
    const { container } = afficher('Voici les étapes :\n1. Panier\n2. Paiement');

    expect(container.querySelector('ol')).not.toBeNull();
    expect(container.querySelectorAll('li')).toHaveLength(2);
    expect(screen.getByText('Panier')).toBeInTheDocument();
  });

  it('transforme les puces en liste à puces', () => {
    const { container } = afficher('- Cardiologie\n- Pédiatrie');

    expect(container.querySelector('ul')).not.toBeNull();
    expect(container.querySelectorAll('li')).toHaveLength(2);
  });

  it('rend un chemin interne cliquable sans avaler la ponctuation', () => {
    afficher('Vos commandes sont sur /my-orders.');

    const lien = screen.getByRole('link', { name: '/my-orders' });
    expect(lien).toHaveAttribute('href', '/my-orders');
    expect(screen.getByText(/\./)).toBeInTheDocument();
  });

  it('rend un lien Markdown interne avec son libellé', () => {
    afficher('Consultez le [catalogue](/catalog) pour commencer.');

    expect(screen.getByRole('link', { name: 'catalogue' })).toHaveAttribute('href', '/catalog');
  });

  it('conserve les espaces entre les mots', () => {
    const { container } = afficher('Pour effectuer un achat sur la plateforme');

    expect(container.textContent).toBe('Pour effectuer un achat sur la plateforme');
  });
});

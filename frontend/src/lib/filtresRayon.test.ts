import { describe, expect, it } from 'vitest';
import {
  lireFiltres, ecrireFiltres, etiquettes, auMoinsUnFiltre, FILTRES_VIDES,
} from './filtresRayon';

/**
 * Les filtres passent par l'adresse, et doivent en revenir intacts.
 *
 * <p>C'est ce qui permet de partager un rayon filtré, de le mettre en favori, et surtout de
 * retrouver ses filtres en revenant d'une fiche produit. Un aller-retour qui les perd est le
 * moment précis où le visiteur s'en va.</p>
 */
describe('filtres du rayon dans l’adresse', () => {
  it('rend ce qu’on a écrit, à l’identique', () => {
    const filtres = { prixMin: 10, prixMax: 500, surDevis: true, promo: true, tri: 'prix-croissant' as const };
    expect(lireFiltres(ecrireFiltres(filtres, 0))).toEqual(filtres);
  });

  it('n’écrit QUE ce qui est posé', () => {
    // Une adresse qui porterait tous les critères à vide serait illisible, et impossible à
    // dicter ou à relire.
    expect(ecrireFiltres(FILTRES_VIDES, 0).toString()).toBe('');
  });

  it('distingue « en ligne » de « sur devis » de « les deux »', () => {
    expect(lireFiltres(new URLSearchParams('devis=oui')).surDevis).toBe(true);
    expect(lireFiltres(new URLSearchParams('devis=non')).surDevis).toBe(false);
    // Absent : aucun filtre, et surtout pas « false » — qui masquerait les produits sur devis.
    expect(lireFiltres(new URLSearchParams('')).surDevis).toBeUndefined();
  });

  it('retombe sur l’ordre naturel pour un tri inconnu', () => {
    // Adresse bricolée ou ancien favori : la page s’affiche, elle n’échoue pas. Le serveur,
    // lui, refuse — c’est lui qui traduit en SQL.
    expect(lireFiltres(new URLSearchParams('tri=DROP')).tri).toBe('pertinence');
  });

  it('ignore un prix illisible ou négatif plutôt que de filtrer au hasard', () => {
    expect(lireFiltres(new URLSearchParams('prixMin=abc')).prixMin).toBeUndefined();
    expect(lireFiltres(new URLSearchParams('prixMin=-5')).prixMin).toBeUndefined();
  });

  it('numérote les pages à partir de 1 dans l’adresse', () => {
    // La page 0 d’un développeur est la page 1 d’un visiteur : l’adresse se lit.
    expect(ecrireFiltres(FILTRES_VIDES, 2).get('page')).toBe('3');
    expect(ecrireFiltres(FILTRES_VIDES, 0).get('page')).toBeNull();
  });

  it('ne compte pas le tri comme un filtre', () => {
    // Trier ne retire aucun produit : proposer « Réinitialiser » à cause d’un tri serait
    // annoncer un problème qui n’existe pas.
    expect(auMoinsUnFiltre({ tri: 'prix-croissant' })).toBe(false);
    expect(auMoinsUnFiltre({ tri: 'pertinence', promo: true })).toBe(true);
  });

  it('nomme chaque filtre actif en français', () => {
    const liste = etiquettes({ prixMin: 10, prixMax: 90, surDevis: false, promo: true, tri: 'pertinence' });
    expect(liste.map((e) => e.texte)).toEqual([
      'À partir de 10 €', "Jusqu'à 90 €", 'Achat en ligne', 'En promotion',
    ]);
  });
});

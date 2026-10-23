import { describe, it, expect } from 'vitest';
import { reessayer } from './reessais';

/** Une erreur axios, réduite à ce que la règle en lit. */
const erreur = (status?: number) => ({ response: { status } });

/**
 * Combien de fois un appel échoué est retenté.
 *
 * <p><b>Mesuré en production le 23 octobre.</b> Une fiche produit dont l'adresse ne désignait
 * rien mettait 9,7 secondes à afficher sa page « ce produit n'existe pas » : trois tentatives
 * d'un appel voué à échouer, pendant lesquelles le visiteur lisait « Chargement… ». La même
 * page sans appel réseau répondait en 2,4 s.</p>
 */
describe('réessais', () => {
  it("ne retente jamais un 404", () => {
    // Le serveur a compris la question et répond que la chose n'existe pas : la reposer ne
    // la fera pas exister.
    expect(reessayer(0, erreur(404))).toBe(false);
    expect(reessayer(1, erreur(404))).toBe(false);
  });

  it('retente une fois les autres erreurs, et une seule', () => {
    // Deux appels au total : un réseau coupé ou un serveur qui redémarre méritent une
    // seconde chance, pas une troisième.
    expect(reessayer(0, erreur(500))).toBe(true);
    expect(reessayer(1, erreur(500))).toBe(false);
    expect(reessayer(2, erreur(500))).toBe(false);
  });

  it("retente un 401, qui peut venir d'un jeton à renouveler", () => {
    expect(reessayer(0, erreur(401))).toBe(true);
    expect(reessayer(1, erreur(401))).toBe(false);
  });

  it("retente une erreur sans réponse, typiquement un réseau coupé", () => {
    // Pas de `response` du tout : la requête n'est jamais partie.
    expect(reessayer(0, new Error('Network Error'))).toBe(true);
    expect(reessayer(1, new Error('Network Error'))).toBe(false);
  });

  it('ne tombe pas sur une erreur de forme inattendue', () => {
    // Appelée à chaque échec de chaque requête de l'application : lever ici remplacerait une
    // erreur réseau lisible par une exception sans rapport.
    expect(() => reessayer(0, null)).not.toThrow();
    expect(() => reessayer(0, undefined)).not.toThrow();
    expect(() => reessayer(0, 'une chaîne')).not.toThrow();
  });
});

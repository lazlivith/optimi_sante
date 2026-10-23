import { describe, it, expect } from 'vitest';
import { deconnecteSur } from './axiosClient';

/**
 * Quand un 401 doit-il déconnecter, et quand ne le doit-il pas.
 *
 * <p><b>L'incident du 23 octobre.</b> 148 fiches produit portaient un slug contenant un
 * « % » — reste d'un « ® » encodé à l'import. Une fois correctement encodé dans l'adresse, ce
 * « % » devient « %25 », que le pare-feu de Spring Security refuse avant le contrôleur : 401,
 * corps vide, sur la route publique du catalogue. L'intercepteur y lisait une session expirée,
 * effaçait le jeton et renvoyait vers « /login ». Un visiteur anonyme se voyait demander de
 * s'identifier pour lire une fiche publique ; un super-administrateur perdait sa session.</p>
 *
 * <p>La règle tient en deux conditions, et aucun test ne la couvrait : il n'y avait pas de
 * règle, seulement « 401 ⇒ déconnexion ».</p>
 */
describe('déconnexion sur 401', () => {
  const JETON = 'un-jeton';

  it("ne déconnecte pas sur la route publique du catalogue, même avec un jeton valide", () => {
    // L'appel exact de l'incident, slug compris.
    expect(deconnecteSur(
      '/catalog/products/tensiometre-electronique-microlife%25c2%25acae-watch-bp-vascular-abi',
      JETON,
    )).toBe(false);
  });

  it('ne déconnecte sur aucune des autres routes ouvertes au visiteur', () => {
    for (const route of ['/catalog/products', '/public/exercer-en-france', '/devises',
                         '/trainings', '/blog/articles']) {
      expect(deconnecteSur(route, JETON)).toBe(false);
    }
  });

  it("ne déconnecte pas un visiteur sans jeton, même sur une route protégée", () => {
    // Sans jeton, « non authentifié » est la réponse attendue — pas la preuve qu'une session
    // vient de tomber. Rediriger ici ferait boucler toute page qui sonde une route protégée.
    expect(deconnecteSur('/admin/products', null)).toBe(false);
  });

  it('déconnecte bien quand un jeton est refusé sur une route protégée', () => {
    // Le cas pour lequel l'intercepteur existe : la session a expiré pendant la navigation.
    expect(deconnecteSur('/admin/products', JETON)).toBe(true);
    expect(deconnecteSur('/orders/mes-commandes', JETON)).toBe(true);
  });

  it("traite une URL absente comme une route protégée", () => {
    // axios peut remonter une erreur sans config.url : mieux vaut déconnecter à tort que
    // laisser une session morte faire échouer silencieusement chaque appel suivant.
    expect(deconnecteSur(undefined, JETON)).toBe(true);
  });
});

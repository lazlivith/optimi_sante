import { describe, it, expect } from 'vitest';
import { estPagePerimee, MESSAGE_PAGE_A_RECHARGER, REFERENCE_PERIMEE } from './storageService';

/**
 * Le décalage de version se reconnaît sur un code, et sur rien d'autre.
 *
 * Reconnaître ce cas en comparant le texte du message aurait été la facilité, et le piège : le
 * message est écrit pour un candidat, donc il se reformule. Le code ne se reformule pas. Ces
 * tests verrouillent le fait qu'un autre 409 — « Vous êtes déjà inscrit à cette session », par
 * exemple — ne déclenche pas l'invitation à recharger.
 */
describe('estPagePerimee', () => {
  it('reconnaît un 409 portant le code REFERENCE_PERIMEE', () => {
    expect(estPagePerimee({
      response: { status: 409, data: { code: REFERENCE_PERIMEE, message: MESSAGE_PAGE_A_RECHARGER } },
    })).toBe(true);
  });

  it('ignore un autre 409 métier', () => {
    expect(estPagePerimee({
      response: { status: 409, data: { message: 'Vous êtes déjà inscrit à cette session' } },
    })).toBe(false);
  });

  it('ignore le même code sur un autre statut', () => {
    expect(estPagePerimee({
      response: { status: 400, data: { code: REFERENCE_PERIMEE } },
    })).toBe(false);
  });

  it('ne casse pas sur une erreur sans réponse — panne réseau, requête annulée', () => {
    expect(estPagePerimee(new Error('Network Error'))).toBe(false);
    expect(estPagePerimee(undefined)).toBe(false);
    expect(estPagePerimee(null)).toBe(false);
  });

  it('porte le message exact montré au candidat', () => {
    // Doit rester identique à ReferencePerimeeException.MESSAGE côté serveur.
    expect(MESSAGE_PAGE_A_RECHARGER).toBe(
      'La page a été mise à jour, merci de la recharger avant de déposer vos pièces.',
    );
  });
});

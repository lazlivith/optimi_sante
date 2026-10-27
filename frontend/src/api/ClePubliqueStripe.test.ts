import { describe, it, expect } from 'vitest';
import { clePubliqueUtilisable } from './clePubliqueStripe';

/**
 * Ce que vaut la clé publique Stripe qui part dans le paquet servi.
 *
 * <p><b>La panne du 27 octobre.</b> `VITE_STRIPE_PUBLISHABLE_KEY` n'était pas posée chez
 * l'hébergeur du frontend. Vite inscrit la valeur DANS le code compilé : le site s'est
 * construit, déployé et affiché normalement, et `loadStripe('')` a levé un `IntegrationError`
 * au moment de payer, laissant un cadre vide. La seule trace au monde était un message dans la
 * console de qui ouvrait les outils de développement.</p>
 */
describe('la clé publique Stripe', () => {
  it('accepte les deux seules formes que loadStripe comprend', () => {
    expect(clePubliqueUtilisable('pk_test_51Abc')).toBe(true);
    expect(clePubliqueUtilisable('pk_live_51Abc')).toBe(true);
  });

  it('refuse une clé absente ou vide — le cas exact de la panne', () => {
    expect(clePubliqueUtilisable('')).toBe(false);
    expect(clePubliqueUtilisable('   '.trim())).toBe(false);
  });

  it('refuse la clé secrète recopiée à la place de la publique', () => {
    // Même panne, même silence : une clé secrète côté navigateur ne fonctionne pas, et serait
    // en plus un secret publié. Vérifier le préfixe, et pas seulement la présence, l'attrape.
    expect(clePubliqueUtilisable('sk_test_51Abc')).toBe(false);
    expect(clePubliqueUtilisable('sk_live_51Abc')).toBe(false);
  });

  it('refuse une valeur d’exemple laissée en place', () => {
    expect(clePubliqueUtilisable('votre_cle_ici')).toBe(false);
    expect(clePubliqueUtilisable('pk_')).toBe(false);
  });
});

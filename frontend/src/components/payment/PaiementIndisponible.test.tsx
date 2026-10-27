import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen } from '@testing-library/react';

/**
 * Ce que voit un visiteur quand le paiement par carte ne peut pas s'afficher.
 *
 * <p><b>Pourquoi ce test monte le composant plutôt que de lire la fonction.</b> Le défaut
 * n'était pas dans la lecture de la clé : il était dans ce que la page faisait d'une clé vide,
 * c'est-à-dire rien. `loadStripe('')` levait, et le visiteur voyait un cadre vide sur un écran
 * qui avait l'air de fonctionner. Seul un rendu montre cela.</p>
 *
 * <p>La variable est injectée avant l'import du module, parce que Vite la fige au chargement :
 * un `vi.stubEnv` après coup n'aurait aucun effet, et le test passerait pour une mauvaise
 * raison.</p>
 */
describe('le formulaire de paiement sans clé publique', () => {
  beforeEach(() => {
    vi.resetModules();
  });

  afterEach(() => {
    vi.unstubAllEnvs();
    vi.restoreAllMocks();
  });

  it('affiche un message au lieu d’un cadre vide, et signale l’incident', async () => {
    vi.stubEnv('VITE_STRIPE_PUBLISHABLE_KEY', '');

    const signalerIncident = vi.fn();
    vi.doMock('../../api/journalClient', () => ({ signalerIncident }));

    const { StripeEmbeddedCheckout } = await import('./StripeEmbeddedCheckout');
    render(<StripeEmbeddedCheckout clientSecret="cs_test_x" />);

    expect(
      screen.getByText(/paiement par carte est momentanément indisponible/i),
    ).toBeTruthy();
    // Sans ce signalement, la panne reste invisible de notre côté : c'est exactement ce qui
    // s'est passé le 27 octobre.
    expect(signalerIncident).toHaveBeenCalledTimes(1);
    expect(signalerIncident.mock.calls[0][0]).toBe('paiement-carte');
  });

  it('ne tente jamais d’appeler Stripe avec une clé vide', async () => {
    vi.stubEnv('VITE_STRIPE_PUBLISHABLE_KEY', '');

    const loadStripe = vi.fn();
    vi.doMock('@stripe/stripe-js', () => ({ loadStripe }));
    vi.doMock('../../api/journalClient', () => ({ signalerIncident: vi.fn() }));

    await import('./StripeEmbeddedCheckout');

    // C'est cet appel qui levait l'IntegrationError. Ne pas le faire vaut mieux que le
    // rattraper.
    expect(loadStripe).not.toHaveBeenCalled();
  });
});

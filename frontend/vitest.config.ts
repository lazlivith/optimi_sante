import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';

/**
 * Outillage de test, strictement limité au développement.
 *
 * <p><b>Il ne pèse rien sur le site.</b> vitest, jsdom et testing-library sont des
 * dépendances de DÉVELOPPEMENT : elles ne figurent dans aucun import du code livré, et le
 * bundle de production ne les voit jamais. Le garde-fou de poids de la charte le vérifie à
 * chaque build.</p>
 *
 * <p><b>Couverture volontairement étroite.</b> Ces tests portent sur « Exercer en France »,
 * et sur rien d'autre. Couvrir le projet entier d'un coup produirait des centaines de tests
 * écrits sans connaître leur sujet — longs à lancer, faux à la première évolution, et
 * désactivés dans le mois. Le reste viendra quand on y touchera.</p>
 */
export default defineConfig({
  plugins: [react()],
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/preparation.ts'],
    include: ['src/**/*.test.{ts,tsx}'],
  },
});

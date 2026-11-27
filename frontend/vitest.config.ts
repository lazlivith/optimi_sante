import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';

/*
  GARDE-FOU : LETTRE DE LECTEUR EN MINUSCULE (Windows).

  Lancée depuis `d:\…` au lieu de `D:\…`, la suite échoue à la collecte sur tous les fichiers
  avec « Vitest failed to find the current suite ». Ce message ne dit rien de la cause, et il
  a coûté deux jours en novembre 2026. On interrompt donc ici, avec la cause et le remède.

  Corriger le répertoire depuis ce fichier ne marche pas : essayé, vitest a déjà retenu le
  chemin fautif quand il lit sa configuration. La correction vit dans
  `scripts/lancer-tests.mjs`, que `npm run test` appelle ; ce garde-fou ne sert qu'à qui lance
  `npx vitest` directement.

  Hors Windows, la condition est toujours fausse.
*/
if (process.platform === 'win32' && /^[a-z]:/.test(process.cwd())) {
  const lecteur = process.cwd().slice(0, 2);
  throw new Error(
    `Répertoire courant en ${lecteur}\\ au lieu de ${lecteur.toUpperCase()}\\ — les tests `
      + 'échoueraient de façon incompréhensible (« Vitest failed to find the current suite »). '
      + 'Lance "npm run test", ou entre dans le dossier par cd /d/OptimiSanté_Projet/... '
      + 'puis vérifie avec : node -p "process.cwd()"',
  );
}

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

    /*
      DÉLAIS EXPLICITES, ET GÉNÉREUX.

      Neuf de ces tests pilotent un formulaire au clavier (userEvent.type, waitFor,
      findByText). Leurs délais par défaut — 5 s par test, 1 s pour waitFor — sont des
      durées de machine au repos. Une passe a rendu « 6 échecs, 33 réussites » pendant que
      la machine reconstruisait une image Docker ; elle n'a jamais pu être reproduite
      ensuite, en dix-huit exécutions dont trois sous charge volontaire, mais la durée
      totale passe de 4,9 s à 7,2 s selon l'occupation du poste. La sensibilité est donc
      réelle, même si le seuil n'a pas été atteint de nouveau.

      Ces valeurs n'affaiblissent AUCUNE assertion : un test qui échoue vraiment échoue
      toujours, simplement plus tard. Ce qu'elles suppriment, c'est la possibilité qu'un
      test correct devienne rouge parce qu'un autre programme occupait le processeur — un
      échec qui n'apprend rien et qui apprend surtout à ne plus lire les résultats.
    */
    testTimeout: 20_000,
    hookTimeout: 20_000,
  },
});

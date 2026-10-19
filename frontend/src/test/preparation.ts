import { configure } from '@testing-library/react';
import '@testing-library/jest-dom/vitest';
import { cleanup } from '@testing-library/react';
import { afterEach } from 'vitest';

// Chaque test repart d'un document vide : sans cela, un composant monté par le test
// précédent reste dans le DOM, et une recherche par texte trouve deux résultats.
afterEach(cleanup);

/*
  Le délai d'attente de testing-library, porté à 5 s au lieu de 1 s.

  « waitFor » et « findBy* » scrutent le DOM jusqu'à cette limite. Une seconde suffit sur
  une machine au repos ; elle ne suffit pas toujours quand une build tourne à côté. Comme
  « testTimeout » dans vitest.config.ts, cette valeur ne rend aucun test plus permissif :
  elle lui laisse le temps de constater ce qu'il constate déjà.
*/
configure({ asyncUtilTimeout: 5_000 });

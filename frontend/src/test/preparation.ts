import '@testing-library/jest-dom/vitest';
import { cleanup } from '@testing-library/react';
import { afterEach } from 'vitest';

// Chaque test repart d'un document vide : sans cela, un composant monté par le test
// précédent reste dans le DOM, et une recherche par texte trouve deux résultats.
afterEach(cleanup);

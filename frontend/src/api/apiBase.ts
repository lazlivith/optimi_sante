/**
 * Adresse de l'API, unique pour toute l'application.
 *
 * <p>Deux montages sont possibles, et celui-ci ne tranche pas à votre place :</p>
 *
 * - **Même domaine** — le site et l'API derrière le même nom, un relais `/api` devant le backend.
 *   C'est le cas par défaut, et le plus sûr : aucune requête préalable du navigateur, aucune
 *   origine tierce à autoriser. `VITE_API_URL` reste vide.
 * - **Domaines séparés** — l'API hébergée ailleurs que les pages. Il faut alors son adresse
 *   complète, et déclarer le domaine du site dans `CORS_ALLOWED_ORIGINS` côté serveur.
 *
 * <p>Attention : cette valeur est figée <b>à la construction</b> du bundle, pas au démarrage.
 * Changer d'adresse impose de reconstruire le site, pas seulement de le redéployer.</p>
 */
const configuree = (import.meta.env.VITE_API_URL ?? '').trim();

/** Sans barre oblique finale : les appels la fournissent déjà (`${API_BASE}/enrollments`). */
export const API_BASE = configuree === ''
  ? '/api/v1'
  : configuree.replace(/\/+$/, '');

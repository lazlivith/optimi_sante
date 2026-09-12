import type { Plugin } from 'vite';

/**
 * Pages publiques à déclarer aux moteurs de recherche.
 *
 * <p>Seulement celles qu'un visiteur non connecté peut atteindre. Les espaces authentifiés sont
 * exclus par `robots.txt` : les lister dans un sitemap reviendrait à les annoncer tout en
 * demandant de ne pas les lire.</p>
 */
const PAGES_PUBLIQUES = [
  { chemin: '/',                    frequence: 'weekly',  priorite: '1.0' },
  { chemin: '/catalog',             frequence: 'daily',   priorite: '0.9' },
  { chemin: '/formations',          frequence: 'weekly',  priorite: '0.9' },
  { chemin: '/devenir-partenaire',  frequence: 'monthly', priorite: '0.6' },
  { chemin: '/login',               frequence: 'yearly',  priorite: '0.3' },
  { chemin: '/register',            frequence: 'yearly',  priorite: '0.3' },
];

/** Espaces derrière authentification : rien à explorer, et rien à indexer. */
const ESPACES_PRIVES = [
  '/admin', '/partner', '/doctor', '/checkout', '/cart', '/profile', '/my-orders',
];

/**
 * Produit `robots.txt` et `sitemap.xml` au moment du build, à partir de `VITE_SITE_URL`.
 *
 * <p><b>Pourquoi les générer plutôt que les figer.</b> Ces deux fichiers portaient
 * `https://www.optimisante.fr`, un domaine qui redirige en 301 vers l'ancien site WordPress.
 * Publier cela reviendrait à envoyer les robots — et le référencement de nos pages — chez
 * quelqu'un d'autre. Un fichier figé finit toujours par désigner le mauvais domaine ; une
 * variable unique ne le peut pas.</p>
 *
 * <p><b>Quand `VITE_SITE_URL` est vide</b> — le cas tant qu'aucun domaine définitif n'est
 * arrêté — aucun sitemap n'est produit et `robots.txt` ne référence aucun domaine. Un sitemap
 * n'existe pas en version relative : la norme impose des adresses absolues. Mieux vaut pas de
 * sitemap du tout qu'un sitemap qui désigne le site d'autrui.</p>
 *
 * <p>Le jour de la mise en ligne, il suffit de renseigner cette variable : le sitemap apparaît,
 * `robots.txt` le référence, et la balise canonique se met à être émise (voir
 * {@code usePageMeta}).</p>
 */
export function fichiersSeo(): Plugin {
  let origine = '';

  return {
    name: 'optimi-fichiers-seo',
    apply: 'build',

    configResolved(config) {
      // Normalisée une fois pour toutes : sans barre finale, pour que la concaténation avec
      // un chemin commençant par « / » ne produise jamais de double barre.
      origine = (config.env.VITE_SITE_URL as string | undefined)?.trim().replace(/\/+$/, '') ?? '';
    },

    generateBundle() {
      const lignes = [
        'User-agent: *',
        'Allow: /',
        '',
        '# Espaces privés (authentification requise) : inutile de les faire explorer',
        ...ESPACES_PRIVES.map((chemin) => `Disallow: ${chemin}`),
      ];

      if (origine) {
        lignes.push('', `Sitemap: ${origine}/sitemap.xml`);

        const urls = PAGES_PUBLIQUES.map(({ chemin, frequence, priorite }) =>
          [
            '  <url>',
            `    <loc>${origine}${chemin}</loc>`,
            `    <changefreq>${frequence}</changefreq>`,
            `    <priority>${priorite}</priority>`,
            '  </url>',
          ].join('\n'),
        ).join('\n');

        this.emitFile({
          type: 'asset',
          fileName: 'sitemap.xml',
          source: '<?xml version="1.0" encoding="UTF-8"?>\n'
            + '<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n'
            + `${urls}\n`
            + '</urlset>\n',
        });
      } else {
        lignes.push(
          '',
          '# Aucun domaine officiel n\'est encore arrêté (VITE_SITE_URL vide) : pas de sitemap.',
          '# Un sitemap exige des adresses absolues ; en produire un sans domaine reviendrait',
          '# soit à inventer un domaine, soit à désigner celui d\'un tiers.',
        );
      }

      this.emitFile({ type: 'asset', fileName: 'robots.txt', source: lignes.join('\n') + '\n' });
    },
  };
}

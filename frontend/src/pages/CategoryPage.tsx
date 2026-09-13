import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useQuery, keepPreviousData } from '@tanstack/react-query';
import { ArrowRight, ChevronLeft, ChevronRight } from 'lucide-react';
import { catalogService } from '../api/catalogService';
import type { Category } from '../api/catalogService';
import { ProductCard } from '../components/catalog/ProductCard';
import { visuelAFaire } from '../api/productMediaService';
import { usePageMeta } from '../hooks/usePageMeta';

const TAILLE_PAGE = 24;
const RAYONS_SUGGERES = 6;

/**
 * Colonnes de la bande « en promotion », selon le nombre de promotions.
 *
 * <p>La plupart des rayons n'en ont qu'une ou deux. Dans une grille à quatre colonnes, une
 * carte seule occupe un quart de la largeur et laisse les trois autres vides : la rangée passe
 * pour un défaut de chargement. Les classes sont écrites en entier, jamais composées à la
 * volée — Tailwind lit le source et ne générerait pas une classe formée à l'exécution.</p>
 */
const COLONNES_PROMO: Record<number, string> = {
  1: 'grid-cols-1 max-w-[15rem]',
  2: 'grid-cols-2 max-w-lg',
  3: 'grid-cols-2 sm:grid-cols-3 max-w-3xl',
};

/** Le catalogue importé contient encore des entités HTML dans les noms. */
const nomLisible = (nom: string) => nom.replace(/&amp;/g, '&');

/**
 * Page d'un rayon : ce qu'il contient, ce qui y est en promotion, où aller ensuite.
 *
 * <p><b>Pourquoi une page à part et non un filtre du catalogue.</b> Les deux répondent à des
 * questions différentes. Le catalogue sert à chercher — une barre de recherche, un sélecteur,
 * une grille. Un rayon se parcourt : on veut voir ce qu'il y a, ce qui est en promotion, et
 * ressortir vers un rayon voisin. La page de catalogue continue de fonctionner exactement
 * comme avant, filtre par catégorie compris.</p>
 *
 * <p><b>Ce que cette page ne peut pas montrer : les sous-rayons.</b> Les maquettes de référence
 * ouvrent sur une bande « Sous catégories ». La table {@code categories} porte bien un
 * {@code parent_id} et le serveur sait renvoyer l'arborescence — mais les 211 catégories du
 * catalogue sont <b>toutes racines, aucune n'a de parent</b>. Il n'y a donc aucun sous-rayon à
 * afficher, et en fabriquer par ressemblance de nom inventerait un classement que personne
 * n'a décidé. La bande du bas propose donc d'<i>autres</i> rayons, ce qu'elle annonce.</p>
 */
export function CategoryPage() {
  const { slug } = useParams<{ slug: string }>();

  // Passer d'un rayon à l'autre ne remonte pas le composant : la page courante doit donc
  // revenir à zéro, sinon on arrive page 4 d'un rayon qui n'en compte qu'une. Le numéro de
  // page est retenu AVEC le rayon auquel il appartient, et déduit au rendu : le remettre à
  // zéro depuis un effet déclencherait un second rendu, le temps duquel la page 4 d'un rayon
  // qui n'existe plus serait demandée au serveur.
  const [position, setPosition] = useState<{ slug?: string; page: number }>({ slug, page: 0 });
  const page = position.slug === slug ? position.page : 0;
  const allerALaPage = (n: number) => setPosition({ slug, page: n });

  const { data: categories, isLoading: chargementRayons } = useQuery({
    queryKey: ['categories-boutique'],
    queryFn: () => catalogService.getCategories(),
  });

  const rayon = categories?.find((c) => c.slug === slug);
  const titre = rayon ? nomLisible(rayon.name) : '';

  usePageMeta(
    titre || 'Rayon',
    rayon
      ? `${rayon.productCount ?? 0} produits du rayon ${titre} : dispositifs médicaux certifiés CE, livrés et garantis par Optimi Santé.`
      : undefined,
  );

  const { data: promotions } = useQuery({
    queryKey: ['rayon-promos', rayon?.id],
    queryFn: () => catalogService.getProducts({ categoryId: rayon!.id, promo: true, size: 8 }),
    enabled: Boolean(rayon),
  });

  const { data: produits, isLoading: chargementProduits } = useQuery({
    queryKey: ['rayon-produits', rayon?.id, page],
    queryFn: () => catalogService.getProducts({
      categoryId: rayon!.id, page, size: TAILLE_PAGE,
    }),
    enabled: Boolean(rayon),
    // Sans cela, changer de page vide la grille puis la remplit : la page saute de toute sa
    // hauteur à chaque clic sur « suivant ».
    placeholderData: keepPreviousData,
  });

  if (chargementRayons) return <SqueletteRayon />;

  if (!rayon) {
    return (
      <div className="mx-auto max-w-2xl px-6 py-24 text-center">
        <h1 className="text-2xl font-bold text-brand-dark">Ce rayon n'existe pas</h1>
        <p className="mt-3 text-slate-600">
          L'adresse demandée ne correspond à aucun rayon du catalogue. Il a pu être renommé.
        </p>
        <Link
          to="/catalog"
          className="mt-6 inline-block rounded-full bg-brand px-6 py-3 text-sm font-bold
                     text-white transition-colors hover:bg-brand-fonce"
        >
          Voir le catalogue
        </Link>
      </div>
    );
  }

  const enPromotion = promotions?.content ?? [];
  const listeProduits = produits?.content ?? [];
  const total = produits?.totalElements ?? rayon.productCount ?? 0;
  const dernierePage = Math.max(0, (produits?.totalPages ?? 1) - 1);

  const autresRayons = (categories ?? [])
    .filter((c) => c.id !== rayon.id && (c.productCount ?? 0) > 0 && !visuelAFaire(c.imageUrl))
    .sort((a, b) => (b.productCount ?? 0) - (a.productCount ?? 0))
    .slice(0, RAYONS_SUGGERES);

  return (
    <div className="min-h-screen bg-slate-50">
      <BandeauRayon rayon={rayon} titre={titre} total={total} />

      {enPromotion.length > 0 && (
        <section className="container mx-auto max-w-6xl px-6 py-10">
          <div className="mb-6 flex flex-wrap items-center justify-between gap-3">
            <div>
              <h2 className="text-xl font-bold text-brand-dark lg:text-2xl">
                En promotion dans ce rayon
              </h2>
              {/* Le filtre est celui de la page « Promotions » et du prix réellement
                  facturé : les trois ne peuvent pas diverger. */}
              <p className="mt-1 text-sm text-slate-600">
                Remises actives aujourd'hui, sur {enPromotion.length} produit
                {enPromotion.length > 1 ? 's' : ''} du rayon.
              </p>
            </div>
            <Link
              to="/catalog?promo=true"
              className="flex items-center gap-1.5 text-sm font-semibold text-brand hover:underline"
            >
              Toutes les promotions <ArrowRight className="h-4 w-4" aria-hidden="true" />
            </Link>
          </div>
          <div className={`grid gap-4 ${
            COLONNES_PROMO[Math.min(enPromotion.length, 4)]
            ?? 'grid-cols-2 sm:grid-cols-3 lg:grid-cols-4'
          }`}>
            {enPromotion.slice(0, 4).map((p) => <ProductCard key={p.id} product={p} />)}
          </div>
        </section>
      )}

      <section className="container mx-auto max-w-6xl px-6 pb-12">
        <h2 className="mb-6 text-xl font-bold text-brand-dark lg:text-2xl">
          Tous les produits du rayon
        </h2>

        {chargementProduits && listeProduits.length === 0 ? (
          <GrilleSquelette />
        ) : listeProduits.length === 0 ? (
          <div className="rounded-2xl border border-slate-200 bg-white py-16 text-center">
            <p className="font-semibold text-brand-dark">Ce rayon est vide pour le moment.</p>
            <Link to="/catalog" className="mt-4 inline-block text-sm font-semibold text-brand hover:underline">
              Parcourir le catalogue
            </Link>
          </div>
        ) : (
          <>
            <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4">
              {listeProduits.map((p) => <ProductCard key={p.id} product={p} />)}
            </div>

            {dernierePage > 0 && (
              <nav
                aria-label="Pages du rayon"
                className="mt-10 flex items-center justify-center gap-4"
              >
                <button
                  type="button"
                  onClick={() => allerALaPage(Math.max(0, page - 1))}
                  disabled={page === 0}
                  className="flex h-10 w-10 items-center justify-center rounded-full border
                             border-slate-200 bg-white text-brand-dark transition-colors
                             hover:border-brand hover:text-brand disabled:opacity-40"
                >
                  <ChevronLeft className="h-5 w-5" aria-hidden="true" />
                  <span className="sr-only">Page précédente</span>
                </button>
                {/* aria-live : au clavier, le focus reste sur le bouton et rien n'annoncerait
                    que la grille a changé. */}
                <p aria-live="polite" className="text-sm font-medium text-slate-600">
                  Page {page + 1} sur {dernierePage + 1}
                </p>
                <button
                  type="button"
                  onClick={() => allerALaPage(Math.min(dernierePage, page + 1))}
                  disabled={page >= dernierePage}
                  className="flex h-10 w-10 items-center justify-center rounded-full border
                             border-slate-200 bg-white text-brand-dark transition-colors
                             hover:border-brand hover:text-brand disabled:opacity-40"
                >
                  <ChevronRight className="h-5 w-5" aria-hidden="true" />
                  <span className="sr-only">Page suivante</span>
                </button>
              </nav>
            )}
          </>
        )}
      </section>

      {autresRayons.length > 0 && (
        <section className="border-t border-slate-200 bg-white">
          <div className="container mx-auto max-w-6xl px-6 py-10">
            <h2 className="mb-6 text-xl font-bold text-brand-dark lg:text-2xl">Autres rayons</h2>
            <ul className="grid grid-cols-3 gap-x-3 gap-y-6 sm:grid-cols-4 lg:grid-cols-6">
              {autresRayons.map((c) => <VignetteRayon key={c.id} rayon={c} />)}
            </ul>
          </div>
        </section>
      )}
    </div>
  );
}

/**
 * En-tête du rayon : fil d'Ariane, nom, nombre de produits, et la photo qui le représente.
 *
 * <p>Bannière claire et courte, comme celle des promotions : la précédente génération de
 * bandeaux occupait près de 800 pixels et le visiteur devait défiler pour atteindre la
 * boutique.</p>
 */
function BandeauRayon(
  { rayon, titre, total }: { rayon: Category; titre: string; total: number },
) {
  return (
    <header className="border-b border-slate-200 bg-brand-cream">
      <div className="container mx-auto grid max-w-6xl items-center gap-6 px-6 py-8
                      lg:grid-cols-[1.4fr_1fr]">
        <div>
          <nav aria-label="Fil d'Ariane" className="mb-4 text-sm text-slate-600">
            <ol className="flex flex-wrap items-center gap-2">
              <li><Link to="/" className="underline-offset-2 hover:underline">Accueil</Link></li>
              <li aria-hidden="true" className="text-slate-400">›</li>
              <li><Link to="/catalog" className="underline-offset-2 hover:underline">Catalogue</Link></li>
              <li aria-hidden="true" className="text-slate-400">›</li>
              {/* aria-current : la marche courante est annoncée comme telle, au lieu de
                  passer pour un lien de plus. */}
              <li aria-current="page" className="font-semibold text-brand-dark">{titre}</li>
            </ol>
          </nav>

          <h1 className="text-3xl font-extrabold tracking-tight text-brand-dark lg:text-4xl">
            {titre}
          </h1>
          <p className="mt-2 text-sm text-slate-600">
            {total} produit{total > 1 ? 's' : ''} disponible{total > 1 ? 's' : ''} dans ce rayon
          </p>
        </div>

        {!visuelAFaire(rayon.imageUrl) && (
          <div className="hidden h-40 items-center justify-center lg:flex">
            {/* alt="" : la photo illustre le rayon que le titre vient de nommer. La décrire
                ferait entendre deux fois la même chose. */}
            <img
              src={rayon.imageUrl ?? undefined}
              alt=""
              className="max-h-full max-w-full object-contain"
            />
          </div>
        )}
      </div>
    </header>
  );
}

function VignetteRayon({ rayon }: { rayon: Category }) {
  return (
    <li>
      <Link
        to={`/category/${rayon.slug}`}
        className="group flex h-full flex-col items-center gap-2 rounded-2xl p-2 transition-all
                   hover:bg-slate-50 hover:shadow-md hover:ring-1 hover:ring-brand/20
                   focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand"
      >
        <div className="flex h-24 w-full items-center justify-center">
          <img
            src={rayon.imageUrl ?? undefined}
            alt=""
            loading="lazy"
            className="max-h-full max-w-full object-contain transition-transform duration-300
                       group-hover:scale-105"
          />
        </div>
        <span className="text-center text-sm font-medium leading-tight text-brand-dark
                         group-hover:text-brand">
          {nomLisible(rayon.name)}
        </span>
      </Link>
    </li>
  );
}

function GrilleSquelette() {
  return (
    <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4">
      {Array.from({ length: 8 }).map((_, i) => (
        <div key={i} className="animate-pulse rounded-2xl border border-slate-200 bg-white p-4">
          <div className="mb-4 h-32 rounded-xl bg-slate-100" />
          <div className="mb-2 h-4 w-4/5 rounded bg-slate-100" />
          <div className="h-4 w-1/2 rounded bg-slate-100" />
        </div>
      ))}
    </div>
  );
}

function SqueletteRayon() {
  return (
    <div className="min-h-screen bg-slate-50">
      <div className="border-b border-slate-200 bg-brand-cream">
        <div className="container mx-auto max-w-6xl animate-pulse px-6 py-10">
          <div className="mb-4 h-3 w-48 rounded bg-slate-200" />
          <div className="h-9 w-72 rounded bg-slate-200" />
        </div>
      </div>
      <div className="container mx-auto max-w-6xl px-6 py-10">
        <GrilleSquelette />
      </div>
    </div>
  );
}

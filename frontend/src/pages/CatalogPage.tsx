import { useEffect, useRef, useState } from 'react';
import { useInfiniteQuery, useQuery } from '@tanstack/react-query';
import { Link, useSearchParams } from 'react-router-dom';
import { catalogService, cheminProduit } from '../api/catalogService';
import type { Product } from '../api/catalogService';
import { Search, Mail, Plus, SlidersHorizontal } from 'lucide-react';
import { useCart } from '../context/CartContext';
import { PromoProductVisual } from '../components/catalog/PromoProductVisual';
import { usePageMeta } from '../hooks/usePageMeta';

/** Produits par requete. Vingt-quatre remplit six rangees de quatre sans laisser de trou. */
const TAILLE_PAGE = 24;

export function CatalogPage() {
  usePageMeta('Catalogue médical', "Découvrez notre catalogue de dispositifs médicaux certifiés CE : équipements, consommables et matériel professionnel pour cabinets et structures de soin.");
  // Le menu propose « Promotions » vers /catalog?promo=true depuis le depart, mais la page
  // ignorait ce parametre : l'entree menait au catalogue entier. Elle le lit desormais.
  const [searchParams] = useSearchParams();
  const promoOnly = searchParams.get('promo') === 'true';

  const [search, setSearch] = useState('');
  // La categorie arrive par l'URL quand on vient d'une vignette « Incontournables » : elle
  // amorce l'etat, sans le figer — le visiteur reste libre d'en changer dans la liste.
  const [selectedCategory, setSelectedCategory] = useState<string | undefined>(
    () => searchParams.get('category') ?? undefined);

  // Si l'URL change pendant que la page est ouverte — deux vignettes cliquees a la suite —
  // React ne remonte pas le composant : sans cet effet, la seconde ne changerait rien.
  useEffect(() => {
    setSelectedCategory(searchParams.get('category') ?? undefined);
  }, [searchParams]);
  const { addToCart } = useCart();

  const { data: categories } = useQuery({
    queryKey: ['categories'],
    queryFn: async () => {
      const cats = await catalogService.getCategories();
      if (!cats) return [];
      const uniqueMap = new Map<string, typeof cats[0]>();
      cats.forEach(cat => {
        const decodedName = cat.name.replace(/&amp;/g, '&');
        if (!uniqueMap.has(decodedName)) {
          uniqueMap.set(decodedName, { ...cat, name: decodedName });
        }
      });
      return Array.from(uniqueMap.values()).sort((a, b) => a.name.localeCompare(b.name));
    }
  });

  // Le catalogue se parcourt en descendant, page apres page. La version precedente
  // demandait `size: 20` SANS aucune pagination : elle annoncait « 1487 produits » et n'en
  // montrait que vingt, sans le moindre moyen d'atteindre les suivants.
  const {
    data, isLoading, error, fetchNextPage, hasNextPage, isFetchingNextPage,
  } = useInfiniteQuery({
    queryKey: ['catalog-products', { search, selectedCategory, promoOnly }],
    queryFn: ({ pageParam }) => catalogService.getProducts({
      search, categoryId: selectedCategory, promo: promoOnly || undefined,
      page: pageParam, size: TAILLE_PAGE,
    }),
    initialPageParam: 0,
    // `undefined` signifie « plus rien apres » : c'est ce qui eteint le chargement.
    getNextPageParam: (derniere) =>
      derniere.number + 1 < derniere.totalPages ? derniere.number + 1 : undefined,
  });

  const produits = data?.pages.flatMap((page) => page.content) ?? [];
  const selectedCategoryName = categories?.find(c => c.id === selectedCategory)?.name;
  const nombreResultats = data?.pages[0]?.totalElements;

  // Le bouton du bas sert de sentinelle : quand il approche de l'ecran, la page suivante
  // part d'elle-meme. Il reste un vrai bouton, cliquable — un defilement infini sans
  // commande manuelle est inatteignable au clavier et bloque quiconque ne fait pas defiler.
  const sentinelle = useRef<HTMLButtonElement>(null);
  useEffect(() => {
    const cible = sentinelle.current;
    if (!cible || !hasNextPage) return;
    const guetteur = new IntersectionObserver(
      ([entree]) => { if (entree.isIntersecting) fetchNextPage(); },
      // On declenche 600 px avant que le bouton n'entre dans l'ecran : la page suivante est
      // la au moment ou le visiteur y arrive, au lieu de lui montrer un vide puis un saut.
      { rootMargin: '600px' },
    );
    guetteur.observe(cible);
    return () => guetteur.disconnect();
  }, [hasNextPage, fetchNextPage, produits.length]);

  return (
    <div className="bg-slate-50 min-h-screen">
      {/* En-tete de rayon. Presente seulement quand une categorie est choisie : sur le
          catalogue entier, un fil d'Ariane a une seule marche n'apprend rien. */}
      {selectedCategoryName && (
        <div className="border-b border-slate-200 bg-white">
          <div className="container mx-auto max-w-6xl px-6 py-8">
            <nav aria-label="Fil d'Ariane" className="mb-4 text-sm text-slate-600">
              <ol className="flex flex-wrap items-center gap-2">
                <li><Link to="/" className="underline-offset-2 hover:underline">Accueil</Link></li>
                <li aria-hidden="true" className="text-slate-400">›</li>
                <li><Link to="/catalog" className="underline-offset-2 hover:underline">Catalogue</Link></li>
                <li aria-hidden="true" className="text-slate-400">›</li>
                {/* aria-current : le lecteur d'ecran annonce la marche courante au lieu de
                    laisser croire qu'elle est cliquable. */}
                <li aria-current="page" className="font-semibold text-brand-dark">
                  {selectedCategoryName}
                </li>
              </ol>
            </nav>

            <h1 className="text-3xl font-extrabold tracking-tight text-brand-dark lg:text-4xl">
              {selectedCategoryName}
            </h1>
            {nombreResultats !== undefined && (
              <p className="mt-2 text-sm text-slate-600">
                {nombreResultats === 0
                  ? 'Aucun produit dans ce rayon pour le moment.'
                  : `${nombreResultats} produit${nombreResultats > 1 ? 's' : ''} dans ce rayon`}
              </p>
            )}
          </div>
        </div>
      )}

      {/* Barre de filtres — sticky sous la navbar, comme une vraie boutique en ligne */}
      {/* Hauteur reelle de l'en-tete collante, mesuree dans le navigateur : 80px de rangee
          principale + 48px de navigation secondaire + les 2px de leurs bordures basses.

          `top-32` valait 128px et oubliait les bordures : l'en-tete recouvrait les deux
          premiers pixels de cette barre. Deux pixels ne se voient guere, mais ils se
          reverifient — la valeur precedente (top-16, 64px) etait fausse de 66 pixels et
          personne ne l'avait remarquee non plus. Toute modification de la hauteur de
          l'en-tete doit repasser ici. */}
      <div className="sticky top-[130px] z-30 bg-white border-b border-slate-200 shadow-sm">
        <div className="container mx-auto px-6 py-4 max-w-6xl">
          <div className="flex flex-col sm:flex-row gap-3">
            <div className="relative flex-1">
              <Search className="absolute left-4 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" />
              <input
                type="search"
                placeholder="Rechercher un dispositif, une référence..."
                className="w-full pl-11 pr-4 py-2.5 bg-slate-50 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-brand/20 focus:border-brand focus:bg-white transition-all"
                value={search}
                onChange={(e) => setSearch(e.target.value)}
              />
            </div>

            <div className="relative sm:w-72">
              <SlidersHorizontal className="absolute left-4 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400 pointer-events-none" />
              <select
                value={selectedCategory ?? ''}
                onChange={(e) => setSelectedCategory(e.target.value || undefined)}
                className="w-full pl-11 pr-4 py-2.5 bg-slate-50 border border-slate-200 rounded-xl text-sm text-slate-700 appearance-none cursor-pointer focus:outline-none focus:ring-2 focus:ring-brand/20 focus:border-brand focus:bg-white transition-all"
              >
                <option value="">Toutes les catégories</option>
                {categories?.map((cat) => (
                  <option key={cat.id} value={cat.id}>{cat.name}</option>
                ))}
              </select>
            </div>
          </div>

          {selectedCategoryName && (
            <div className="flex items-center gap-2 mt-3">
              <span className="text-xs text-slate-500">Filtré par :</span>
              <span className="inline-flex items-center gap-1.5 px-3 py-1 bg-brand-light text-brand-dark text-xs font-semibold rounded-full">
                {selectedCategoryName}
                <button onClick={() => setSelectedCategory(undefined)} className="hover:text-brand" aria-label="Retirer le filtre">×</button>
              </span>
            </div>
          )}
        </div>
      </div>

      <div className="container mx-auto px-6 py-8 max-w-6xl">
        {/* En mode promotions, la page doit se presenter comme telle : sans ce bandeau, le
            visiteur venu du menu voit une liste courte sans comprendre pourquoi. */}
        {promoOnly && (
          <div className="mb-6 rounded-2xl bg-gradient-to-r from-orange-500 to-amber-500 text-white px-6 py-5 flex flex-wrap items-center justify-between gap-4">
            <div>
              <h1 className="text-2xl font-bold">Promotions en cours</h1>
              <p className="text-sm text-white/90 mt-0.5">
                Uniquement les produits dont la remise est active aujourd'hui.
              </p>
            </div>
            <Link
              to="/catalog"
              className="shrink-0 bg-white/15 hover:bg-white/25 backdrop-blur px-4 py-2 rounded-xl text-sm font-semibold transition-colors"
            >
              Voir tout le catalogue
            </Link>
          </div>
        )}

        {isLoading ? (
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-5">
            {Array.from({ length: 8 }).map((_, i) => (
              <div key={i} className="bg-white rounded-2xl border border-slate-200 p-5 animate-pulse">
                <div className="aspect-square bg-slate-100 rounded-xl mb-4" />
                <div className="h-3 bg-slate-100 rounded w-1/3 mb-3" />
                <div className="h-4 bg-slate-100 rounded w-4/5 mb-2" />
                <div className="h-4 bg-slate-100 rounded w-1/2" />
              </div>
            ))}
          </div>
        ) : error ? (
          <div className="text-center py-20 text-red-500 bg-red-50 rounded-2xl border border-red-100">Erreur lors du chargement du catalogue.</div>
        ) : produits.length === 0 ? (
          promoOnly ? (
            // Une liste vide en mode promotions n'est pas une recherche infructueuse : c'est
            // qu'aucune remise n'est active. Le dire evite de chercher un filtre fautif.
            <div className="text-center py-20">
              <p className="text-slate-700 font-semibold">Aucune promotion en cours.</p>
              <p className="text-slate-500 text-sm mt-1">Revenez bientôt, ou parcourez le catalogue complet.</p>
              <Link to="/catalog" className="inline-block mt-5 bg-brand text-white px-5 py-2.5 rounded-full font-bold text-sm hover:bg-brand-fonce transition-colors">
                Voir le catalogue
              </Link>
            </div>
          ) : (
            <div className="text-center py-20 text-slate-500">Aucun produit ne correspond à votre recherche.</div>
          )
        ) : (
          <>
            {/* Ce que le visiteur voit, et sur combien : un compteur seul laisse croire que
                tout est affiche. aria-live l'annonce a chaque page chargee, sans quoi le
                defilement infini est muet pour un lecteur d'ecran. */}
            {/* Une seule expression, pas un mot coupe par un retour a la ligne : la version
                precedente ecrivait « affiche » sur une ligne et « s » sur la suivante, et
                affichait donc « 24 produits affiches » — sans accent. */}
            <p aria-live="polite" className="text-sm text-slate-500 mb-5">
              {produits.length > 1
                ? `${produits.length} produits affichés sur ${nombreResultats ?? produits.length}`
                : `${produits.length} produit sur ${nombreResultats ?? produits.length}`}
            </p>
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-5">
              {produits.map((product: Product) => (
                <div
                  key={product.id}
                  className="group flex flex-col bg-white rounded-2xl border border-slate-200 shadow-sm hover:shadow-lg hover:-translate-y-0.5 transition-all relative overflow-hidden"
                >
                  {/* `contain` et non `cover` : les visuels du catalogue sont des packshots
                      aux cadrages variables, et un recadrage automatique amputait l'article —
                      or c'est sur cette vignette que l'acheteur le reconnaît. */}
                  <Link to={cheminProduit(product.slug)} className="block relative aspect-square bg-white overflow-hidden p-4">
                    <PromoProductVisual
                      product={product}
                      className="w-full h-full group-hover:scale-105 transition-transform duration-500"
                      iconClassName="w-10 h-10"
                      objectFit="contain"
                    />
                    {product.isOnPromo && (
                      <div className="absolute top-3 left-3">
                        <span className="px-2.5 py-1 text-[10px] font-bold text-white bg-rose-600 rounded-md tracking-wider shadow-sm">
                          PROMO
                        </span>
                      </div>
                    )}
                    <div className="absolute top-3 right-3 flex flex-col gap-2">
                      {product.isQuoteOnly ? (
                        <span className="px-2.5 py-1 text-[10px] font-bold text-brand-accent border border-brand-orange/30 bg-orange-50 rounded-md tracking-wider shadow-sm">
                          SUR DEVIS
                        </span>
                      ) : product.stockQuantity < 5 ? (
                        <span className="px-2.5 py-1 text-[10px] font-bold text-red-600 border border-red-200 bg-red-50 rounded-md tracking-wider shadow-sm">
                          STOCK BAS
                        </span>
                      ) : null}
                    </div>
                  </Link>

                  <div className="flex-1 flex flex-col p-5">
                    <div className="flex-1 flex flex-col mb-4">
                      {/* text-slate-400 sur blanc plafonne a 2,56:1, tres en dessous des
                          4,5:1 exiges (WCAG 1.4.3), et 11 px sur telephone n'aidaient pas.
                          slate-500 atteint 4,76:1. */}
                      <span className="text-xs text-slate-500 mb-1.5">{(product.category?.name || 'Général').replace(/&amp;/g, '&')}</span>
                      <Link to={cheminProduit(product.slug)} className="font-semibold text-sm text-brand-dark group-hover:text-brand transition-colors leading-snug line-clamp-2">
                        {product.name}
                      </Link>
                    </div>

                    <div className="flex items-end justify-between pt-3 border-t border-slate-100">
                      <div className="flex flex-col">
                        {(product.b2bDiscountRate > 0 || product.isOnPromo) && (
                          <span className="text-xs text-slate-400 line-through">
                            {product.basePrice.toFixed(0)} €
                          </span>
                        )}
                        <span className={`text-lg font-bold ${product.isOnPromo ? 'text-rose-600' : 'text-brand-dark'}`}>
                          {product.isQuoteOnly ? 'Sur devis' : `${product.finalPrice.toFixed(0)} €`}
                        </span>
                      </div>

                      {product.isQuoteOnly ? (
                        <button
                          onClick={() => addToCart(product, 1)}
                          className="flex items-center justify-center w-9 h-9 border border-slate-200 rounded-lg text-slate-600 hover:bg-slate-50 transition-colors"
                          title="Demander un devis"
                        >
                          <Mail className="w-4 h-4" />
                        </button>
                      ) : (
                        <button
                          onClick={() => addToCart(product, 1)}
                          disabled={product.stockQuantity < 1}
                          className="flex items-center justify-center w-9 h-9 border border-slate-200 rounded-lg text-slate-600 hover:border-brand hover:bg-brand hover:text-white transition-colors disabled:opacity-40 disabled:hover:bg-transparent disabled:hover:text-slate-600 disabled:hover:border-slate-200"
                          title="Ajouter au panier"
                        >
                          <Plus className="w-4 h-4" />
                        </button>
                      )}
                    </div>
                  </div>
                </div>
              ))}
            </div>

            {hasNextPage && (
              <div className="mt-10 flex justify-center">
                <button
                  ref={sentinelle}
                  type="button"
                  onClick={() => fetchNextPage()}
                  disabled={isFetchingNextPage}
                  className="rounded-full border border-slate-200 bg-white px-6 py-3 text-sm
                             font-semibold text-brand-dark transition-colors hover:border-brand
                             hover:text-brand disabled:opacity-60"
                >
                  {isFetchingNextPage ? 'Chargement…' : 'Afficher plus de produits'}
                </button>
              </div>
            )}

            {/* Annonce la fin seulement si le visiteur a effectivement charge quelque chose :
                sur une recherche qui tient en une page, « vous avez atteint la fin » n'apprend
                rien. On compte les pages CHARGEES, et non les produits — la derniere page du
                catalogue n'en compte que 23, ce qui ne suffisait pas a declencher le message. */}
            {!hasNextPage && (data?.pages.length ?? 0) > 1 && (
              <p className="mt-10 text-center text-sm text-slate-500">
                Vous avez atteint la fin du catalogue.
              </p>
            )}
          </>
        )}
      </div>
    </div>
  );
}

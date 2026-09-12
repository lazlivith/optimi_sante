import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { ArrowRight, ChevronLeft, ChevronRight, Clock, Tag } from 'lucide-react';
import type { Product } from '../../api/catalogService';
import { PromoProductVisual } from '../catalog/PromoProductVisual';

/**
 * Carrousel d'accueil alimenté par les produits réellement en promotion.
 *
 * <p><b>L'image est présentée, pas utilisée comme papier peint.</b> La première version
 * l'étalait en fond plein cadre (`object-cover`) : un gros plan de catalogue s'y retrouvait
 * zoomé et recadré, et le texte blanc devenait illisible dès que la photo était claire. Les
 * visuels du catalogue sont hétérogènes — packshots sur fond blanc, photos d'ambiance,
 * cadrages serrés — et aucun n'est maîtrisé. Elle occupe donc un panneau dédié, en
 * `object-contain` sur fond clair : le produit est visible en entier quel que soit le
 * cadrage, et la lisibilité du texte ne dépend plus de la photo.</p>
 *
 * <p>Un carrousel vide serait pire que des diapositives figées : quand aucune promotion n'est
 * active, la page d'accueil retombe sur ses bannières statiques (voir `HomePage`).</p>
 */
export function PromoHeroSlider({ products }: { products: Product[] }) {
  const [current, setCurrent] = useState(0);
  const total = products.length;
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const relancer = () => {
    if (intervalRef.current) clearInterval(intervalRef.current);
    // Une seule promotion ne défile pas : faire clignoter un carrousel à une diapositive
    // donne l'impression d'un bug.
    if (total > 1) {
      intervalRef.current = setInterval(() => setCurrent((c) => (c + 1) % total), 6000);
    }
  };

  useEffect(() => {
    relancer();
    return () => { if (intervalRef.current) clearInterval(intervalRef.current); };
  }, [total]);

  // Si le nombre de promotions diminue pendant que la page est ouverte, l'index courant peut
  // pointer au-delà du tableau : on le ramène dans les bornes plutôt que de rendre du vide.
  useEffect(() => { if (current >= total) setCurrent(0); }, [total, current]);

  if (total === 0) return null;

  const produit = products[Math.min(current, total - 1)];
  const remise = produit.basePrice > 0
    ? Math.round((1 - produit.finalPrice / produit.basePrice) * 100)
    : 0;

  const allerA = (i: number) => { setCurrent(i); relancer(); };

  return (
    <section className="relative overflow-hidden rounded-3xl mx-4 md:mx-8 mt-4 bg-brand-dark">
      <div className="grid lg:grid-cols-2 items-center gap-6 lg:gap-10 px-6 sm:px-10 lg:px-14 py-8 lg:py-10">

        {/* Texte */}
        <div className="order-2 lg:order-1 max-w-xl">
          <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-orange-500 text-white text-xs font-bold mb-4">
            <Tag className="w-3.5 h-3.5" />
            {remise > 0 ? `−${remise} % de remise` : 'Promotion'}
          </span>

          <h1 className="text-2xl sm:text-3xl lg:text-4xl font-bold text-white leading-tight mb-2 line-clamp-3">
            {produit.name}
          </h1>

          {produit.category?.name && (
            <p className="text-slate-400 text-sm mb-5">{produit.category.name}</p>
          )}

          <div className="flex items-baseline flex-wrap gap-x-3 gap-y-1 mb-6">
            <span className="text-3xl lg:text-4xl font-bold text-orange-400">
              {produit.finalPrice.toFixed(0)} €
            </span>
            <span className="text-lg text-slate-500 line-through">
              {produit.basePrice.toFixed(0)} €
            </span>
          </div>

          <div className="flex flex-wrap items-center gap-3">
            <Link
              to={`/product/${produit.slug}`}
              className="inline-flex items-center gap-2 px-6 py-3 rounded-xl text-base font-bold text-white bg-orange-500 hover:bg-orange-600 transition-colors shadow-lg shadow-orange-900/30"
            >
              Achetez maintenant <ArrowRight className="w-4 h-4" />
            </Link>
            <Link
              to="/catalog?promo=true"
              className="inline-flex items-center gap-2 px-5 py-3 rounded-xl text-sm font-semibold text-white bg-white/10 hover:bg-white/20 transition-colors"
            >
              Toutes les promotions
            </Link>
          </div>

          {produit.promoEndsAt && (
            <p className="flex items-center gap-1.5 mt-5 text-xs text-slate-400">
              <Clock className="w-3.5 h-3.5" />
              Offre valable jusqu'au {new Date(produit.promoEndsAt).toLocaleDateString('fr-FR')}
            </p>
          )}
        </div>

        {/* Panneau image : fond clair et `contain`, pour que le produit soit entier quelle que
            soit la photo, et que le texte ne repose plus sur un dégradé posé dessus. */}
        <div className="order-1 lg:order-2 relative">
          <div className="relative rounded-2xl bg-white overflow-hidden aspect-[4/3] lg:aspect-[5/4] flex items-center justify-center p-5 lg:p-7">
            <PromoProductVisual
              key={produit.id}
              product={produit}
              objectFit="contain"
              className="w-full h-full"
              iconClassName="w-16 h-16"
            />
            {remise > 0 && (
              <span className="absolute top-4 left-4 px-2.5 py-1 rounded-lg bg-orange-500 text-white text-xs font-bold shadow">
                −{remise} %
              </span>
            )}
          </div>
        </div>
      </div>

      {total > 1 && (
        <>
          <button
            type="button" aria-label="Promotion précédente"
            onClick={() => allerA((current - 1 + total) % total)}
            className="absolute left-2 md:left-4 top-1/2 -translate-y-1/2 w-10 h-10 rounded-full bg-white/90 hover:bg-white shadow-lg flex items-center justify-center text-slate-800 transition-colors z-10"
          >
            <ChevronLeft className="w-5 h-5" />
          </button>
          <button
            type="button" aria-label="Promotion suivante"
            onClick={() => allerA((current + 1) % total)}
            className="absolute right-2 md:right-4 top-1/2 -translate-y-1/2 w-10 h-10 rounded-full bg-white/90 hover:bg-white shadow-lg flex items-center justify-center text-slate-800 transition-colors z-10"
          >
            <ChevronRight className="w-5 h-5" />
          </button>

          <div className="absolute bottom-4 left-1/2 -translate-x-1/2 flex gap-2">
            {products.map((p, i) => (
              <button
                key={p.id} type="button"
                onClick={() => allerA(i)}
                aria-label={`Promotion ${i + 1} sur ${total}`}
                aria-current={i === current}
                className={`h-2 rounded-full transition-all ${i === current ? 'bg-orange-500 w-6' : 'bg-white/30 w-2 hover:bg-white/50'}`}
              />
            ))}
          </div>
        </>
      )}
    </section>
  );
}

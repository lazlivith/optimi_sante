import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { ArrowRight, ChevronLeft, ChevronRight, Clock } from 'lucide-react';
import type { Product } from '../../api/catalogService';
import { ProductImage } from '../common/ProductImage';

/**
 * Carrousel d'accueil alimenté par les produits réellement en promotion.
 *
 * <p>Il remplace trois diapositives écrites en dur. Ce que l'administrateur met en promotion
 * depuis son catalogue apparaît ici sans intervention : c'est le même filtre que la page
 * « Promotions » et que le prix effectivement facturé, donc les trois ne peuvent pas
 * diverger.</p>
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
    <section className="relative overflow-hidden rounded-3xl mx-4 md:mx-8 mt-4 bg-slate-900">
      {/* L'image occupe tout le cadre, le texte se pose dessus — la mise en page demandée.
          Le dégradé sombre à gauche garantit la lisibilité quelle que soit la photo, qui
          n'est pas maîtrisée : elle vient du catalogue. */}
      <div className="relative h-[380px] md:h-[440px]">
        <ProductImage
          src={produit.imageUrl}
          alt={produit.name}
          className="absolute inset-0 w-full h-full object-cover"
        />
        <div className="absolute inset-0 bg-gradient-to-r from-slate-950/85 via-slate-950/55 to-transparent" />

        <div className="relative h-full container mx-auto px-8 md:px-12 flex flex-col justify-center max-w-3xl">
          <span className="text-sm font-bold text-amber-300 mb-2">
            {remise > 0 ? `−${remise} % · Promotion` : 'Promotion'}
          </span>

          <h1 className="text-3xl md:text-5xl font-bold text-white leading-tight mb-3 line-clamp-3">
            {produit.name}
          </h1>

          {produit.category?.name && (
            <p className="text-slate-300 text-sm md:text-base mb-5">{produit.category.name}</p>
          )}

          <div className="flex items-baseline gap-3 mb-7">
            <span className="text-lg md:text-xl text-slate-400 line-through">
              {produit.basePrice.toFixed(0)} €
            </span>
            <span className="text-3xl md:text-4xl font-bold text-amber-400">
              {produit.finalPrice.toFixed(0)} €
            </span>
          </div>

          <div className="flex flex-wrap items-center gap-3">
            <Link
              to={`/product/${produit.slug}`}
              className="inline-flex items-center gap-2 px-7 py-3.5 rounded-full text-base font-bold text-white bg-orange-500 hover:bg-orange-600 transition-colors shadow-lg"
            >
              Achetez maintenant <ArrowRight className="w-5 h-5" />
            </Link>
            <Link
              to="/catalog?promo=true"
              className="inline-flex items-center gap-2 px-5 py-3.5 rounded-full text-sm font-semibold text-white bg-white/15 hover:bg-white/25 backdrop-blur transition-colors"
            >
              Toutes les promotions
            </Link>
          </div>

          {produit.promoEndsAt && (
            <p className="flex items-center gap-1.5 mt-5 text-xs text-slate-300">
              <Clock className="w-3.5 h-3.5" />
              Jusqu'au {new Date(produit.promoEndsAt).toLocaleDateString('fr-FR')}
            </p>
          )}
        </div>

        {total > 1 && (
          <>
            <button
              type="button" aria-label="Promotion précédente"
              onClick={() => allerA((current - 1 + total) % total)}
              className="absolute left-3 md:left-5 top-1/2 -translate-y-1/2 w-11 h-11 rounded-full bg-white/90 hover:bg-white shadow-lg flex items-center justify-center text-slate-800 transition-colors z-10"
            >
              <ChevronLeft className="w-5 h-5" />
            </button>
            <button
              type="button" aria-label="Promotion suivante"
              onClick={() => allerA((current + 1) % total)}
              className="absolute right-3 md:right-5 top-1/2 -translate-y-1/2 w-11 h-11 rounded-full bg-white/90 hover:bg-white shadow-lg flex items-center justify-center text-slate-800 transition-colors z-10"
            >
              <ChevronRight className="w-5 h-5" />
            </button>

            <div className="absolute bottom-5 right-6 flex gap-2 bg-white/90 rounded-full px-3 py-2">
              {products.map((p, i) => (
                <button
                  key={p.id} type="button"
                  onClick={() => allerA(i)}
                  aria-label={`Promotion ${i + 1} sur ${total}`}
                  aria-current={i === current}
                  className={`h-2 rounded-full transition-all ${i === current ? 'bg-orange-500 w-5' : 'bg-slate-300 w-2 hover:bg-slate-400'}`}
                />
              ))}
            </div>
          </>
        )}
      </div>
    </section>
  );
}

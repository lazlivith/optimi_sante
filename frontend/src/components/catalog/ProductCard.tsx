import { Link } from 'react-router-dom';
import { Mail, Plus } from 'lucide-react';
import type { Product } from '../../api/catalogService';
import { PromoProductVisual } from './PromoProductVisual';
import { useCart } from '../../context/CartContext';

/**
 * Carte produit des pages de rayon.
 *
 * <p><b>La hauteur de l'image est fixe, jamais un ratio.</b> Un {@code aspect-[3/4]} se calcule
 * sur la largeur de la colonne : sur un écran large, une colonne de 450 pixels réclamait une
 * image de 600 et produisait des cartes de 700 pixels de haut, dont le produit sortait du
 * cadrage. Une hauteur en {@code rem} ne dépend de rien.</p>
 *
 * <p><b>Pas de cœur « favori ».</b> Les maquettes de référence en portent un, mais il n'existe
 * aucune liste d'envies côté serveur : le poser afficherait un bouton qui ne mémorise rien,
 * et un client qui y range un produit le retrouverait perdu au rechargement. Le bouton présent
 * ici — panier, ou demande de devis selon la fiche — agit réellement.</p>
 */
export function ProductCard({ product }: { product: Product }) {
  const { addToCart } = useCart();
  const remise = product.isOnPromo && product.basePrice > 0
    ? Math.round((1 - product.finalPrice / product.basePrice) * 100)
    : 0;

  return (
    <article
      className="group flex flex-col overflow-hidden rounded-2xl border border-slate-200
                 bg-white shadow-sm transition-shadow hover:shadow-md"
    >
      <Link
        to={`/product/${product.slug}`}
        className="relative flex h-36 items-center justify-center p-4 sm:h-40 lg:h-44"
      >
        <PromoProductVisual
          product={product}
          objectFit="contain"
          className="h-full w-full transition-transform duration-300 group-hover:scale-105"
          iconClassName="w-9 h-9"
        />

        {remise > 0 && (
          // Encre sur l'orange de la charte : 5,35:1. Du blanc n'y tiendrait pas (3,00:1).
          <span className="absolute left-3 top-3 rounded-full bg-brand-orange px-2.5 py-1
                           text-[11px] font-bold text-brand-dark">
            −{remise} %
          </span>
        )}
        {product.isQuoteOnly && (
          <span className="absolute right-3 top-3 rounded-full border border-brand-orange/40
                           bg-white px-2.5 py-1 text-[10px] font-bold uppercase tracking-wide
                           text-brand-accent">
            Sur devis
          </span>
        )}
      </Link>

      <div className="flex flex-1 flex-col px-4 pb-4">
        {/* Hauteur minimale sur deux lignes : sans elle, un nom court et un nom long
            décalent les prix l'un par rapport à l'autre et la rangée perd son alignement. */}
        <Link
          to={`/product/${product.slug}`}
          className="line-clamp-2 min-h-[2.5rem] text-sm font-semibold leading-snug
                     text-brand-dark transition-colors hover:text-brand"
        >
          {product.name}
        </Link>

        <div className="mt-auto flex items-end justify-between gap-3 pt-3">
          <div className="flex flex-col">
            {(product.isOnPromo || product.b2bDiscountRate > 0) && !product.isQuoteOnly && (
              <span className="text-xs text-slate-500 line-through">
                {product.basePrice.toFixed(0)} €
              </span>
            )}
            <span className={`text-lg font-bold ${
              product.isOnPromo ? 'text-brand-accent' : 'text-brand-dark'
            }`}>
              {product.isQuoteOnly ? 'Sur devis' : `${product.finalPrice.toFixed(0)} €`}
            </span>
          </div>

          <button
            type="button"
            onClick={() => addToCart(product, 1)}
            disabled={!product.isQuoteOnly && product.stockQuantity < 1}
            aria-label={product.isQuoteOnly
              ? `Demander un devis pour ${product.name}`
              : `Ajouter ${product.name} au panier`}
            className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg border
                       border-slate-200 text-slate-600 transition-colors hover:border-brand
                       hover:bg-brand hover:text-white disabled:opacity-40
                       disabled:hover:border-slate-200 disabled:hover:bg-transparent
                       disabled:hover:text-slate-600"
          >
            {product.isQuoteOnly
              ? <Mail className="h-4 w-4" aria-hidden="true" />
              : <Plus className="h-4 w-4" aria-hidden="true" />}
          </button>
        </div>
      </div>
    </article>
  );
}

import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { catalogService } from '../api/catalogService';
import type { Product } from '../api/catalogService';
import { Search, Mail, Plus, SlidersHorizontal } from 'lucide-react';
import { useCart } from '../context/CartContext';
import { ProductImage } from '../components/common/ProductImage';

export function CatalogPage() {
  const [search, setSearch] = useState('');
  const [selectedCategory, setSelectedCategory] = useState<string | undefined>(undefined);
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

  const { data, isLoading, error } = useQuery({
    queryKey: ['products', { search, selectedCategory }],
    queryFn: () => catalogService.getProducts({ search, categoryId: selectedCategory, size: 20 })
  });

  const selectedCategoryName = categories?.find(c => c.id === selectedCategory)?.name;

  return (
    <div className="bg-slate-50 min-h-screen">
      {/* Barre de filtres — sticky sous la navbar, comme une vraie boutique en ligne */}
      <div className="sticky top-16 z-30 bg-white border-b border-slate-200 shadow-sm">
        <div className="container mx-auto px-6 py-4 max-w-6xl">
          <div className="flex flex-col sm:flex-row gap-3">
            <div className="relative flex-1">
              <Search className="absolute left-4 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" />
              <input
                type="search"
                placeholder="Rechercher un dispositif, une référence..."
                className="w-full pl-11 pr-4 py-2.5 bg-slate-50 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-brand-green/20 focus:border-brand-green focus:bg-white transition-all"
                value={search}
                onChange={(e) => setSearch(e.target.value)}
              />
            </div>

            <div className="relative sm:w-72">
              <SlidersHorizontal className="absolute left-4 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400 pointer-events-none" />
              <select
                value={selectedCategory ?? ''}
                onChange={(e) => setSelectedCategory(e.target.value || undefined)}
                className="w-full pl-11 pr-4 py-2.5 bg-slate-50 border border-slate-200 rounded-xl text-sm text-slate-700 appearance-none cursor-pointer focus:outline-none focus:ring-2 focus:ring-brand-green/20 focus:border-brand-green focus:bg-white transition-all"
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
                <button onClick={() => setSelectedCategory(undefined)} className="hover:text-brand-green" aria-label="Retirer le filtre">×</button>
              </span>
            </div>
          )}
        </div>
      </div>

      <div className="container mx-auto px-6 py-8 max-w-6xl">
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
        ) : data?.content?.length === 0 ? (
          <div className="text-center py-20 text-slate-500">Aucun produit ne correspond à votre recherche.</div>
        ) : (
          <>
            <p className="text-sm text-slate-500 mb-5">{data?.totalElements ?? 0} produit{(data?.totalElements ?? 0) > 1 ? 's' : ''}</p>
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-5">
              {data?.content?.map((product: Product) => (
                <div
                  key={product.id}
                  className="group flex flex-col bg-white rounded-2xl border border-slate-200 shadow-sm hover:shadow-lg hover:-translate-y-0.5 transition-all relative overflow-hidden"
                >
                  <Link to={`/product/${product.slug}`} className="block relative aspect-square bg-slate-50 overflow-hidden">
                    <ProductImage
                      src={product.imageUrl}
                      alt={product.name}
                      className="w-full h-full group-hover:scale-105 transition-transform duration-500"
                      iconClassName="w-10 h-10"
                      objectFit="cover"
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
                        <span className="px-2.5 py-1 text-[10px] font-bold text-brand-orange border border-brand-orange/30 bg-orange-50 rounded-md tracking-wider shadow-sm">
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
                      <span className="text-[11px] text-slate-400 mb-1.5">{(product.category?.name || 'Général').replace(/&amp;/g, '&')}</span>
                      <Link to={`/product/${product.slug}`} className="font-semibold text-sm text-brand-dark group-hover:text-brand-green transition-colors leading-snug line-clamp-2">
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
                          className="flex items-center justify-center w-9 h-9 border border-slate-200 rounded-lg text-slate-600 hover:border-brand-green hover:bg-brand-green hover:text-white transition-colors disabled:opacity-40 disabled:hover:bg-transparent disabled:hover:text-slate-600 disabled:hover:border-slate-200"
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
          </>
        )}
      </div>
    </div>
  );
}

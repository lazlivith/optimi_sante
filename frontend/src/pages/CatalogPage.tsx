import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { catalogService, type Product } from '../api/catalogService';
import { Search, Box, Mail, Plus } from 'lucide-react';
import { useCart } from '../context/CartContext';

export function CatalogPage() {
  const [search, setSearch] = useState('');
  const { addToCart } = useCart();
  
  const { data, isLoading, error } = useQuery({
    queryKey: ['products', { search }],
    queryFn: () => catalogService.getProducts({ search, size: 20 })
  });

  return (
    <div className="container mx-auto px-6 py-12 max-w-6xl space-y-8">
      <div className="flex flex-col md:flex-row md:items-end justify-between gap-6">
        <div>
          <span className="text-xs font-semibold text-slate-400 tracking-widest uppercase mb-2 block">BOUTIQUE</span>
          <h1 className="text-3xl md:text-4xl font-bold text-brand-dark mb-3">Catalogue des dispositifs médicaux</h1>
          <p className="text-slate-500 max-w-xl">Parcourez librement le catalogue. La connexion n'est demandée qu'au moment de commander.</p>
        </div>
      </div>

      {/* Filters & Search */}
      <div className="flex flex-col lg:flex-row gap-4 items-center">
        <div className="relative flex-1 w-full">
          <Search className="absolute left-4 top-1/2 -translate-y-1/2 w-5 h-5 text-slate-400" />
          <input 
            type="search" 
            placeholder="Rechercher un dispositif, une référence..." 
            className="w-full pl-12 pr-4 py-3 bg-white border border-slate-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-brand-green/20 focus:border-brand-green transition-all"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
        </div>
        
        <div className="flex flex-wrap items-center gap-2 w-full lg:w-auto overflow-x-auto pb-2 lg:pb-0">
          <button className="whitespace-nowrap px-4 py-2.5 rounded-xl text-sm font-medium bg-brand-light text-brand-dark border border-brand-light">Tous</button>
          <button className="whitespace-nowrap px-4 py-2.5 rounded-xl text-sm font-medium bg-white text-slate-600 border border-slate-200 hover:border-slate-300">Diagnostic</button>
          <button className="whitespace-nowrap px-4 py-2.5 rounded-xl text-sm font-medium bg-white text-slate-600 border border-slate-200 hover:border-slate-300">Chirurgie mineure</button>
          <button className="whitespace-nowrap px-4 py-2.5 rounded-xl text-sm font-medium bg-white text-slate-600 border border-slate-200 hover:border-slate-300">Imagerie portable</button>
          <button className="whitespace-nowrap px-4 py-2.5 rounded-xl text-sm font-medium bg-white text-slate-600 border border-slate-200 hover:border-slate-300">Consommables</button>
        </div>
      </div>

      {isLoading ? (
        <div className="flex justify-center py-20">
          <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-brand-green"></div>
        </div>
      ) : error ? (
        <div className="text-center py-20 text-red-500 bg-red-50 rounded-2xl border border-red-100">Erreur lors du chargement du catalogue.</div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          {data?.content?.map((product: Product) => (
            <div 
              key={product.id} 
              className="group flex flex-col bg-white rounded-2xl border border-slate-200 p-6 shadow-sm hover:shadow-md transition-all relative overflow-hidden"
            >
              <div className="flex justify-between items-start mb-6">
                <div className="w-12 h-12 rounded-full bg-brand-light flex items-center justify-center text-brand-green">
                  <Box className="w-6 h-6" />
                </div>
                
                {product.isQuoteOnly ? (
                  <span className="px-3 py-1 text-[10px] font-bold text-brand-orange border border-brand-orange/30 bg-orange-50 rounded-md tracking-wider">
                    SUR DEVIS
                  </span>
                ) : product.stockQuantity < 5 ? (
                  <span className="px-3 py-1 text-[10px] font-bold text-red-600 border border-red-200 bg-red-50 rounded-md tracking-wider">
                    STOCK BAS
                  </span>
                ) : null}
              </div>

              <div className="flex-1 flex flex-col mb-6">
                <span className="text-xs font-mono text-slate-400 mb-2">{product.sku}</span>
                <Link to={`/product/${product.slug}`} className="font-bold text-lg text-brand-dark group-hover:text-brand-green transition-colors leading-tight mb-2">
                  {product.name}
                </Link>
                <span className="text-xs text-slate-500">{product.category?.name || 'Général'} · Unité</span>
              </div>
              
              <div className="mt-auto flex items-end justify-between pt-4 border-t border-slate-100">
                <div className="flex flex-col">
                  {product.b2bDiscountRate > 0 && (
                    <span className="text-xs text-slate-400 line-through mb-1">
                      {product.basePrice.toFixed(0)} €
                    </span>
                  )}
                  <span className="text-xl font-bold text-brand-dark">
                    {product.isQuoteOnly ? 'Tarif sur demande' : `${product.finalPrice.toFixed(0)} €`}
                  </span>
                </div>
                
                {product.isQuoteOnly ? (
                  <button 
                    onClick={() => addToCart(product, 1)}
                    className="flex items-center gap-2 px-4 py-2 border border-slate-200 rounded-lg text-sm font-semibold text-slate-700 hover:bg-slate-50 transition-colors"
                  >
                    <Mail className="w-4 h-4" />
                    Demander un devis
                  </button>
                ) : (
                  <button 
                    onClick={() => addToCart(product, 1)}
                    disabled={product.stockQuantity < 1}
                    className="flex items-center gap-2 px-4 py-2 border border-slate-200 rounded-lg text-sm font-semibold text-slate-700 hover:border-brand-green hover:text-brand-green transition-colors disabled:opacity-50"
                  >
                    <Plus className="w-4 h-4" />
                    Ajouter
                  </button>
                )}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

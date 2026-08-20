import { useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { catalogService } from '../api/catalogService';
import { useCart } from '../context/CartContext';
import { ArrowLeft, Box } from 'lucide-react';

export function ProductDetailPage() {
  const { slug } = useParams<{ slug: string }>();
  const { addToCart } = useCart();
  const [quantity, setQuantity] = useState(1);

  const { data: product, isLoading, error } = useQuery({
    queryKey: ['product', slug],
    queryFn: () => catalogService.getProductBySlug(slug as string),
    enabled: !!slug
  });

  if (isLoading) return <div className="py-20 text-center">Chargement...</div>;
  if (error || !product) return <div className="py-20 text-center text-red-500">Produit introuvable.</div>;

  const handleAddToCart = () => {
    addToCart(product, quantity);
  };

  return (
    <div className="container mx-auto px-6 py-12 max-w-5xl">
      <Link to="/catalog" className="inline-flex items-center text-sm font-medium text-slate-500 hover:text-brand-dark mb-8 transition-colors">
        <ArrowLeft className="w-4 h-4 mr-2" />
        Retour au catalogue
      </Link>
      
      <div className="bg-white rounded-3xl shadow-sm border border-slate-200 p-8 md:p-12">
        <div className="grid md:grid-cols-2 gap-12 lg:gap-20">
          <div className="aspect-square bg-brand-light rounded-2xl flex items-center justify-center text-brand-green">
             <Box className="w-32 h-32 opacity-50" />
          </div>
          
          <div className="flex flex-col">
            <div className="mb-6">
              <span className="text-sm font-semibold text-brand-green tracking-widest uppercase mb-3 block">
                {product.category?.name || 'BOUTIQUE'}
              </span>
              <h1 className="text-3xl lg:text-4xl font-bold text-brand-dark mb-3 leading-tight">{product.name}</h1>
              <p className="text-sm font-mono text-slate-400">Réf: {product.sku}</p>
            </div>
            
            <div className="flex items-baseline gap-4 mb-8">
              {product.isQuoteOnly ? (
                 <span className="text-3xl font-extrabold text-brand-dark">Tarif sur demande</span>
              ) : (
                <>
                  <span className="text-4xl font-extrabold text-brand-dark">{product.finalPrice.toFixed(0)} €</span>
                  {product.b2bDiscountRate > 0 && (
                    <span className="text-xl text-slate-400 line-through">{product.basePrice.toFixed(0)} €</span>
                  )}
                </>
              )}
            </div>

            <div className="prose prose-slate max-w-none mb-10">
              <p className="text-slate-600 leading-relaxed whitespace-pre-wrap">{product.description}</p>
            </div>
            
            <div className="mt-auto flex flex-col sm:flex-row gap-4">
              <div className="flex items-center border border-slate-200 rounded-xl overflow-hidden h-14">
                <button 
                  onClick={() => setQuantity(Math.max(1, quantity - 1))}
                  className="px-5 h-full text-slate-500 hover:bg-slate-50 font-medium transition-colors"
                >-</button>
                <span className="px-4 font-semibold text-brand-dark w-12 text-center">{quantity}</span>
                <button 
                  onClick={() => setQuantity(quantity + 1)}
                  className="px-5 h-full text-slate-500 hover:bg-slate-50 font-medium transition-colors"
                >+</button>
              </div>
              
              <button 
                onClick={handleAddToCart}
                disabled={product.stockQuantity < 1 && !product.isQuoteOnly}
                className="flex-1 h-14 bg-brand-green text-white font-bold rounded-xl hover:bg-[#0f3c35] transition-colors disabled:opacity-50 disabled:cursor-not-allowed shadow-sm"
              >
                {product.isQuoteOnly ? 'Demander un devis' : 'Ajouter au panier'}
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

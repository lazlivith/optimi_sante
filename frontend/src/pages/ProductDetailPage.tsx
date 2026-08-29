import { useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { catalogService } from '../api/catalogService';
import { useCart } from '../context/CartContext';
import { ArrowLeft, ShieldCheck, Truck, Loader2, X } from 'lucide-react';
import { CountdownTimer } from '../components/common/CountdownTimer';
import { Toast, type ToastType } from '../components/common/Toast';
import { ProductImage } from '../components/common/ProductImage';
import { orderService, type QuoteRequestDto } from '../api/orderService';

export function ProductDetailPage() {
  const { slug } = useParams<{ slug: string }>();
  const { addToCart } = useCart();
  const [quantity, setQuantity] = useState(1);
  const [isQuoteModalOpen, setIsQuoteModalOpen] = useState(false);
  const [isSubmittingQuote, setIsSubmittingQuote] = useState(false);
  const [toast, setToast] = useState<{ message: string; type: ToastType } | null>(null);
  const [quoteForm, setQuoteForm] = useState({ companyName: '', siretIce: '', message: '' });

  const { data: product, isLoading, error } = useQuery({
    queryKey: ['product', slug],
    queryFn: () => catalogService.getProductBySlug(slug as string),
    enabled: !!slug
  });

  if (isLoading) return <div className="py-20 text-center">Chargement...</div>;
  if (error || !product) return <div className="py-20 text-center text-red-500">Produit introuvable.</div>;

  const handleAddToCart = () => {
    addToCart(product, quantity);
    setToast({ message: 'Produit ajouté au panier', type: 'success' });
  };

  const handleQuoteSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsSubmittingQuote(true);
    try {
      const request: QuoteRequestDto = {
        items: [{ productId: product.id, quantity }],
        notes: `Société: ${quoteForm.companyName} | SIRET/ICE: ${quoteForm.siretIce}\n\nMessage: ${quoteForm.message}`
      };
      await orderService.quoteRequest(request);
      setIsQuoteModalOpen(false);
      setToast({ message: 'Demande de devis envoyée avec succès.', type: 'success' });
      setQuoteForm({ companyName: '', siretIce: '', message: '' });
    } catch (err) {
      setToast({ message: 'Erreur lors de l\'envoi de la demande.', type: 'error' });
    } finally {
      setIsSubmittingQuote(false);
    }
  };

  return (
    <div className="container mx-auto px-6 py-12 max-w-5xl">
      <Link to="/catalog" className="inline-flex items-center text-sm font-medium text-slate-500 hover:text-brand-dark mb-8 transition-colors">
        <ArrowLeft className="w-4 h-4 mr-2" />
        Retour au catalogue
      </Link>
      
      <div className="bg-white rounded-3xl shadow-sm border border-slate-200 p-8 md:p-12">
        <div className="grid md:grid-cols-2 gap-12 lg:gap-20">
          <div className="aspect-square bg-slate-50 rounded-2xl overflow-hidden relative group">
            <ProductImage
              src={product.imageUrl}
              alt={product.name}
              className="w-full h-full group-hover:scale-105 transition-transform duration-700"
              iconClassName="w-32 h-32 opacity-50"
              objectFit="cover"
            />
          </div>
          
          <div className="flex flex-col">
            <div className="mb-6">
              <span className="text-sm font-semibold text-brand-green tracking-widest uppercase mb-3 block">
                {(product.category?.name || 'BOUTIQUE').replace(/&amp;/g, '&')}
              </span>
              <h1 className="text-3xl lg:text-4xl font-bold text-brand-dark mb-3 leading-tight">{product.name}</h1>
              <p className="text-sm font-mono text-slate-400 mb-3">Réf: {product.sku}</p>
              {!product.isQuoteOnly && product.stockQuantity > 0 && product.stockQuantity < 5 && (
                <div className="flex items-center gap-3 bg-red-50 p-3 rounded-lg border border-red-100">
                  <span className="text-sm font-medium text-red-600">Stock très faible. Réservation temporaire :</span>
                  <CountdownTimer initialMinutes={15} />
                </div>
              )}
            </div>
            
            <div className="flex items-baseline gap-4 mb-6">
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

            <div className="grid grid-cols-2 gap-4 mb-8">
              <div className="flex items-center gap-3 p-4 bg-slate-50 rounded-xl">
                <div className="w-10 h-10 rounded-full bg-white flex items-center justify-center text-brand-green shadow-sm">
                  <ShieldCheck className="w-5 h-5" />
                </div>
                <div className="flex flex-col">
                  <span className="text-xs font-bold text-slate-400 uppercase">Garantie</span>
                  <span className="text-sm font-semibold text-brand-dark">2 ans incluse</span>
                </div>
              </div>
              <div className="flex items-center gap-3 p-4 bg-slate-50 rounded-xl">
                <div className="w-10 h-10 rounded-full bg-white flex items-center justify-center text-brand-green shadow-sm">
                  <Truck className="w-5 h-5" />
                </div>
                <div className="flex flex-col">
                  <span className="text-xs font-bold text-slate-400 uppercase">Livraison</span>
                  <span className="text-sm font-semibold text-brand-dark">Sur devis</span>
                </div>
              </div>
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
                onClick={() => product.isQuoteOnly ? setIsQuoteModalOpen(true) : handleAddToCart()}
                disabled={product.stockQuantity < 1 && !product.isQuoteOnly}
                className="flex-1 h-14 bg-brand-green text-white font-bold rounded-xl hover:bg-[#0f3c35] transition-colors disabled:opacity-50 disabled:cursor-not-allowed shadow-sm"
              >
                {product.isQuoteOnly ? 'Demander un devis' : 'Ajouter au panier'}
              </button>
            </div>
          </div>
        </div>
      </div>

      {/* Quote Modal */}
      {isQuoteModalOpen && (
        <div className="fixed inset-0 z-[60] flex items-center justify-center p-4 bg-slate-900/50 backdrop-blur-sm">
          <div className="bg-white rounded-2xl w-full max-w-lg overflow-hidden shadow-xl border border-slate-200">
            <div className="flex justify-between items-center p-6 border-b border-slate-100 bg-slate-50">
              <h2 className="text-xl font-bold text-brand-dark">Demande de Devis B2B</h2>
              <button onClick={() => setIsQuoteModalOpen(false)} className="text-slate-400 hover:text-brand-dark">
                <X className="w-6 h-6" />
              </button>
            </div>
            <form onSubmit={handleQuoteSubmit} className="p-6 space-y-4">
              <div>
                <label className="block text-sm font-medium text-slate-700 mb-1">Nom de la Société</label>
                <input 
                  type="text" 
                  required 
                  value={quoteForm.companyName} 
                  onChange={(e) => setQuoteForm({...quoteForm, companyName: e.target.value})}
                  className="w-full rounded-md border-slate-300 shadow-sm focus:border-brand-green focus:ring-brand-green p-2 border" 
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-slate-700 mb-1">SIRET / ICE</label>
                <input 
                  type="text" 
                  required 
                  value={quoteForm.siretIce} 
                  onChange={(e) => setQuoteForm({...quoteForm, siretIce: e.target.value})}
                  className="w-full rounded-md border-slate-300 shadow-sm focus:border-brand-green focus:ring-brand-green p-2 border" 
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-slate-700 mb-1">Message spécifique (optionnel)</label>
                <textarea 
                  rows={3}
                  value={quoteForm.message} 
                  onChange={(e) => setQuoteForm({...quoteForm, message: e.target.value})}
                  className="w-full rounded-md border-slate-300 shadow-sm focus:border-brand-green focus:ring-brand-green p-2 border" 
                ></textarea>
              </div>
              <div className="pt-4 border-t border-slate-100 flex justify-end gap-3">
                <button type="button" onClick={() => setIsQuoteModalOpen(false)} className="px-4 py-2 text-sm font-medium text-slate-600 bg-white border border-slate-300 rounded-lg hover:bg-slate-50">
                  Annuler
                </button>
                <button type="submit" disabled={isSubmittingQuote} className="flex items-center px-4 py-2 text-sm font-bold text-white bg-brand-orange rounded-lg hover:bg-orange-600 disabled:opacity-70">
                  {isSubmittingQuote && <Loader2 className="w-4 h-4 mr-2 animate-spin" />}
                  Envoyer la demande
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Toast */}
      {toast && (
        <Toast type={toast.type} message={toast.message} onClose={() => setToast(null)} />
      )}
    </div>
  );
}
